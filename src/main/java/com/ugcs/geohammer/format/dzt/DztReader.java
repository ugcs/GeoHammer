package com.ugcs.geohammer.format.dzt;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;

import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.model.LatLon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DztReader {

	private static final Logger log = LoggerFactory.getLogger(DztReader.class);

	public List<DztChannel> read(File file, DzgFile dzg) throws IOException {
		try (SeekableByteChannel channel = Files.newByteChannel(file.toPath(), StandardOpenOption.READ)) {
			List<DztHeader> headers = readHeaders(channel);
			return readChannels(channel, dzg, headers);
		}
	}

	private List<DztHeader> readHeaders(SeekableByteChannel in) throws IOException {

		DztHeader firstHeader = readHeaderAt(in, 0);
		int numChannels = Math.max(1, firstHeader.rh_nchan);

		List<DztHeader> headers = new ArrayList<>(numChannels);
		headers.add(firstHeader);
		logHeader(firstHeader);
		for (int i = 1; i < numChannels; i++) {
			DztHeader header = readHeaderAt(in, (long) i * DztHeader.MINHEADSIZE);
			headers.add(header);
			logHeader(header);
		}
		return headers;
	}

	private DztHeader readHeaderAt(SeekableByteChannel in, long position) throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate(DztHeader.MINHEADSIZE).order(ByteOrder.LITTLE_ENDIAN);
		in.position(position);
		in.read(buffer);
		DztHeader header = new DztHeader();
		ObjectByteMapper.readObject(header, buffer);
		return header;
	}

	private List<DztChannel> readChannels(SeekableByteChannel in, DzgFile dzg, List<DztHeader> headers) throws IOException {
		if (headers.isEmpty()) {
			return Collections.emptyList();
		}

		int numTraces = computeNumTraces(headers, in.size());
		int numChannels = headers.size();

		List<DztChannel> channels = new ArrayList<>(numChannels);
		for (int headerIndex = 0; headerIndex < numChannels; headerIndex++) {
			DztChannel channel = new DztChannel(headerIndex, headers.get(headerIndex), numTraces);
			channels.add(channel);
		}

		in.position(headers.getFirst().getDataAreaStart());

		for (int traceIndex = 0; traceIndex < numTraces; traceIndex++) {
			if (Thread.currentThread().isInterrupted()) {
				throw new CancellationException();
			}
			LatLon tracePosition = dzg.getLatLon(traceIndex);
			for (int channelIndex = 0; channelIndex < numChannels; channelIndex++) {
				float[] samples = readSamples(in, headers.get(channelIndex));
				Trace trace = new Trace(null, null, samples, tracePosition, null);
				channels.get(channelIndex).addTrace(trace);
			}
		}

		return channels;
	}

	private static int computeNumTraces(List<DztHeader> headers, long fileSize) throws IOException {
		long dataStart = headers.getFirst().getDataAreaStart();
		long scanSize = 0;
		for (DztHeader header : headers) {
			if (SampleCodec.forBitDepth(header.rh_bits) == null) {
				throw new IOException("Unsupported sample bit depth: " + header.rh_bits);
			}
			int numSamples = Math.max(0, header.rh_nsamp - 1);
			scanSize += SampleCodec.traceBufferSize(header.rh_bits, numSamples);
		}
		long dataBytes = fileSize - dataStart;
		if (dataBytes <= 0) {
			throw new IOException("DZT file has no trace data");
		}
		if (dataBytes % scanSize != 0) {
			throw new IOException("Truncated or misaligned DZT: "
					+ dataBytes + " bytes, scanSize=" + scanSize);
		}
		long numTraces = dataBytes / scanSize;
		return (int) numTraces;
	}

	private float[] readSamples(SeekableByteChannel in, DztHeader header) throws IOException {
		SampleCodec codec = SampleCodec.forBitDepth(header.rh_bits);
		if (codec == null) {
			throw new IOException("Unsupported sample bit depth: " + header.rh_bits);
		}
		int numSamples = Math.max(0, header.rh_nsamp - 1);
		int bufferSize = SampleCodec.traceBufferSize(header.rh_bits, numSamples);

		ByteBuffer buffer = ByteBuffer
				.allocate(bufferSize)
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
		}

		return samples;
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
