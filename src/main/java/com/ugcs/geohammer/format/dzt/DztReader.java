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

	List<DztChannel> read(File file, DzgFile dzg) throws IOException {
		try (SeekableByteChannel channel = Files.newByteChannel(file.toPath(), StandardOpenOption.READ)) {
			List<DztHeader> headers = readHeaders(channel);
			logHeader(headers.getFirst());

			List<ChannelDecoder> decoders = prepareDecoders(headers);
			channel.position(DztHeader.getDataPosition(headers.getFirst()));
			readTraces(channel, decoders, dzg);

			return buildChannels(headers, decoders);
		}
	}

	private record ChannelDecoder(SampleCodec codec, int numSamples, int traceBufferSize,
			List<Trace> traces, MinMaxAvg average) {

		void readTrace(SeekableByteChannel in, LatLon latLon) throws IOException {
			ByteBuffer buffer = ByteBuffer
					.allocate(traceBufferSize)
					.order(ByteOrder.LITTLE_ENDIAN);
			in.read(buffer);
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

			traces.add(new Trace(null, null, samples, latLon, null));
		}
	}

	private List<ChannelDecoder> prepareDecoders(List<DztHeader> headers) throws IOException {
		List<ChannelDecoder> decoders = new ArrayList<>(headers.size());
		for (DztHeader header : headers) {
			decoders.add(prepareDecoder(header));
		}
		return decoders;
	}

	private ChannelDecoder prepareDecoder(DztHeader header) throws IOException {
		SampleCodec codec = SampleCodec.forBitDepth(header.rh_bits);
		if (codec == null) {
			throw new IOException("Unsupported sample bit depth: " + header.rh_bits);
		}
		int numSamples = Math.max(0, header.rh_nsamp - 1);
		int bufferSize = SampleCodec.traceBufferSize(header.rh_bits, numSamples);
		return new ChannelDecoder(codec, numSamples, bufferSize,
				new ArrayList<>(), new MinMaxAvg());
	}

	private List<DztHeader> readHeaders(SeekableByteChannel channel) throws IOException {
		ObjectByteMapper byteMapper = new ObjectByteMapper();

		DztHeader firstHeader = readHeaderAt(channel, byteMapper, 0);
		int numChannels = Math.max(1, firstHeader.rh_nchan);

		List<DztHeader> headers = new ArrayList<>(numChannels);
		headers.add(firstHeader);
		for (int i = 1; i < numChannels; i++) {
			headers.add(readHeaderAt(channel, byteMapper, (long) i * DztHeader.MINHEADSIZE));
		}
		return headers;
	}

	private DztHeader readHeaderAt(SeekableByteChannel channel,
			ObjectByteMapper byteMapper, long position) throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate(DztHeader.MINHEADSIZE).order(ByteOrder.LITTLE_ENDIAN);
		channel.position(position);
		channel.read(buffer);
		DztHeader header = new DztHeader();
		byteMapper.readObject(header, buffer);
		return header;
	}

	private void readTraces(SeekableByteChannel channel,
			List<ChannelDecoder> decoders, DzgFile dzg) throws IOException {

		int numChannels = decoders.size();

		int globalIndex = 0;
		while (channel.position() < channel.size()) {
			if (Thread.currentThread().isInterrupted()) {
				throw new CancellationException();
			}
			int ch = globalIndex % numChannels;
			int traceIndex = globalIndex / numChannels;

			decoders.get(ch).readTrace(channel, dzg.getLatLon(traceIndex));
			globalIndex++;
		}
	}

	private List<DztChannel> buildChannels(List<DztHeader> headers,
			List<ChannelDecoder> decoders) {

		List<DztChannel> result = new ArrayList<>(headers.size());
		for (int i = 0; i < headers.size(); i++) {
			ChannelDecoder decoder = decoders.get(i);
			Check.notEmpty(decoder.traces(),
					"Corrupted file: channel " + (i + 1) + " has no traces");
			Traces.shiftSamples(decoder.traces(), -(float) decoder.average().getAverage());

			String name = DztChannel.formatName(i, headers.get(i));
			result.add(new DztChannel(i, name, headers.get(i),
					decoder.traces(), decoder.average()));
		}
		return result;
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
