package com.ugcs.geohammer.format.dzt;

import java.io.Closeable;
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
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Nulls;

public class DztReader implements Closeable {

	private final SeekableByteChannel in;

	private final DzgFile dzg;

	public DztReader(File file, DzgFile dzg) throws IOException {
		Check.notNull(file);

		this.in = Files.newByteChannel(file.toPath(), StandardOpenOption.READ);
		this.dzg = dzg;
	}

	@Override
	public void close() throws IOException {
		in.close();
	}

	public List<DztChannel> read() throws IOException {
		List<DztHeader> headers = readHeaders();
		return readChannels(headers);
	}

	private int numTraces(List<DztHeader> headers) throws IOException {
		if (Nulls.isNullOrEmpty(headers)) {
			return 0;
		}
		long dataStart = headers.getFirst().getDataStart();
		long traceSize = 0; // scan size: size of a single trace with all channels
		for (DztHeader header : headers) {
			int numSamples = Math.max(0, header.rh_nsamp - 1);
			traceSize += header.traceSize(numSamples);
		}
		long fileSize = in.size();
		long dataSize = fileSize - dataStart;
		if (dataSize <= 0) {
			throw new IllegalStateException("DZT file has no trace data");
		}
		if (dataSize % traceSize != 0) {
			throw new IllegalStateException("Truncated or misaligned DZT: "
					+ dataSize + " bytes, traceSize=" + traceSize);
		}
		long numTraces = dataSize / traceSize;
		return (int) numTraces;
	}

	private List<DztHeader> readHeaders() throws IOException {
		DztHeader firstHeader = readHeaderAt(0);
		int numChannels = Math.max(1, firstHeader.rh_nchan);

		List<DztHeader> headers = new ArrayList<>(numChannels);
		headers.add(firstHeader);
		for (int i = 1; i < numChannels; i++) {
			DztHeader header = readHeaderAt((long) i * DztHeader.MINHEADSIZE);
			headers.add(header);
		}
		return headers;
	}

	private DztHeader readHeaderAt(long position) throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate(DztHeader.MINHEADSIZE).order(ByteOrder.LITTLE_ENDIAN);
		in.position(position);
		in.read(buffer);
		return new DztHeader(buffer);
	}

	private List<DztChannel> readChannels(List<DztHeader> headers) throws IOException {
		if (headers.isEmpty()) {
			return Collections.emptyList();
		}

		int numTraces = numTraces(headers);
		int numChannels = headers.size();

		List<DztChannel> channels = new ArrayList<>(numChannels);
		for (int channelIndex = 0; channelIndex < numChannels; channelIndex++) {
			DztChannel channel = new DztChannel(channelIndex, headers.get(channelIndex), numTraces);
			channels.add(channel);
		}

		in.position(headers.getFirst().getDataStart());

		for (int traceIndex = 0; traceIndex < numTraces; traceIndex++) {
			if (Thread.currentThread().isInterrupted()) {
				throw new CancellationException();
			}
			LatLon tracePosition = dzg.getLatLon(traceIndex);
			for (int channelIndex = 0; channelIndex < numChannels; channelIndex++) {
				float[] samples = readSamples(headers.get(channelIndex));
				Trace trace = new Trace(null, null, samples, tracePosition, null);
				channels.get(channelIndex).addTrace(trace);
			}
		}

		return channels;
	}

	private float[] readSamples(DztHeader header) throws IOException {
		int numSamples = Math.max(0, header.rh_nsamp - 1);
		int bufferSize = header.traceSize(numSamples);

		ByteBuffer buffer = ByteBuffer
				.allocate(bufferSize)
				.order(ByteOrder.LITTLE_ENDIAN);
		in.read(buffer);
		buffer.position(0);

		SampleCodec codec = SampleCodec.forBitDepth(header.rh_bits);
		// first value is a trace index, skip it
		codec.read(buffer);

		float[] samples = new float[numSamples];
		for (int sampleIndex = 0; sampleIndex < samples.length; sampleIndex++) {
			samples[sampleIndex] = codec.read(buffer);
		}

		return samples;
	}
}
