package com.ugcs.geohammer.format.dzt;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.math.MinMaxAvg;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Traces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class DztReader {

	private static final Logger log = LoggerFactory.getLogger(DztReader.class);

	static final int MINHEADSIZE = 1024;

	List<DztChannel> read(File file, DzgFile dzg) throws IOException {
		try (SeekableByteChannel channel = Files.newByteChannel(file.toPath(), StandardOpenOption.READ)) {
			List<DztHeader> headers = readHeaders(channel);
			logHeader(headers.getFirst());

			channel.position(getDataPosition(headers.getFirst()));
			List<RawChannel> rawChannels = readTraces(channel, headers, dzg);

			return buildChannels(headers, rawChannels);
		}
	}


	private List<DztHeader> readHeaders(SeekableByteChannel channel) throws IOException {
		ObjectByteMapper byteMapper = new ObjectByteMapper();

		DztHeader firstHeader = readHeaderAt(channel, byteMapper, 0);
		int numChannels = Math.max(1, firstHeader.rh_nchan);

		List<DztHeader> headers = new ArrayList<>(numChannels);
		headers.add(firstHeader);
		for (int i = 1; i < numChannels; i++) {
			headers.add(readHeaderAt(channel, byteMapper, (long) i * MINHEADSIZE));
		}
		return headers;
	}

	private DztHeader readHeaderAt(SeekableByteChannel channel,
			ObjectByteMapper byteMapper, long position) throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate(MINHEADSIZE).order(ByteOrder.LITTLE_ENDIAN);
		channel.position(position);
		channel.read(buffer);
		DztHeader header = new DztHeader();
		byteMapper.readObject(header, buffer);
		return header;
	}

	private record RawChannel(List<Trace> traces, MinMaxAvg average) {}

	private List<RawChannel> readTraces(SeekableByteChannel channel,
			List<DztHeader> headers, DzgFile dzg) throws IOException {

		int numChannels = headers.size();

		List<RawChannel> rawChannels = new ArrayList<>(numChannels);
		SampleCodec[] codecs = new SampleCodec[numChannels];
		int[] bufferSizes = new int[numChannels];
		int[] numSamples = new int[numChannels];

		for (int i = 0; i < numChannels; i++) {
			DztHeader header = headers.get(i);
			codecs[i] = SampleCodec.forBitDepth(header.rh_bits);
			if (codecs[i] == null) {
				throw new IOException("Unsupported sample bit depth: " + header.rh_bits);
			}
			numSamples[i] = Math.max(0, header.rh_nsamp - 1);
			bufferSizes[i] = SampleCodec.traceBufferSize(header.rh_bits, numSamples[i]);
			rawChannels.add(new RawChannel(new ArrayList<>(), new MinMaxAvg()));
		}

		int globalIndex = 0;
		while (channel.position() < channel.size()) {
			if (Thread.currentThread().isInterrupted()) {
				throw new CancellationException();
			}
			int ch = globalIndex % numChannels;
			int scanIndex = globalIndex / numChannels;

			Trace trace = readTrace(channel, codecs[ch], bufferSizes[ch],
					numSamples[ch], rawChannels.get(ch).average(), scanIndex, dzg);
			rawChannels.get(ch).traces().add(trace);
			globalIndex++;
		}
		return rawChannels;
	}

	private Trace readTrace(SeekableByteChannel channel, SampleCodec codec,
			int bufferSize, int numSamples, MinMaxAvg average,
			int traceIndex, DzgFile dzg) throws IOException {

		ByteBuffer buffer = ByteBuffer
				.allocate(bufferSize)
				.order(ByteOrder.LITTLE_ENDIAN);
		channel.read(buffer);
		buffer.position(0);

		// first value is a trace index, skip it
		codec.read(buffer);

		float[] samples = new float[numSamples];
		int sampleIndex = 0;
		while (buffer.position() < buffer.capacity() && sampleIndex < samples.length) {
			int sample = codec.read(buffer);
			samples[sampleIndex++] = sample;
			average.put(sample);
		}

		LatLon latLon = dzg.getLatLon(traceIndex);
		return new Trace(null, null, samples, latLon, null);
	}

	private List<DztChannel> buildChannels(List<DztHeader> headers,
			List<RawChannel> rawChannels) {

		List<DztChannel> result = new ArrayList<>(headers.size());
		for (int i = 0; i < headers.size(); i++) {
			RawChannel raw = rawChannels.get(i);
			Check.notEmpty(raw.traces(), "Corrupted file: channel " + (i + 1) + " has no traces");
			Traces.shiftSamples(raw.traces(), -(float) raw.average().getAverage());

			String name = DztChannel.formatName(i, headers.get(i));
			result.add(new DztChannel(i, name, headers.get(i), raw.traces(), raw.average()));
		}
		return result;
	}

	private long getDataPosition(DztHeader firstHeader) {
		return firstHeader.rh_data < MINHEADSIZE
				? (long) MINHEADSIZE * firstHeader.rh_data
				: (long) firstHeader.rh_nchan * firstHeader.rh_data;
	}

	private void logHeader(DztHeader header) {
		log.debug("| rh_data        {}", header.rh_data);
		log.debug("| rh_bits        {}", header.rh_bits);
		log.debug("| rh_nsamp       {}", header.rh_nsamp);
		log.debug("| rh_zero        {}", header.rh_zero);
		log.debug("| rhf_sps        {}", header.rhf_sps);
		log.debug("| rhf_epsr       {}", header.rhf_epsr);
		log.debug("| rh_spp         {}", header.rh_spp);
		log.debug("| rhf_range (ns) {}", header.rhf_range);
		log.debug("| rhf_depth (m)  {}", header.rhf_depth);
	}
}
