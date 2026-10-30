package com.ugcs.geohammer.format.dzt;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;

import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Nulls;

public class DztWriter implements Closeable {

	// rh_nsamp offset within each MINHEADSIZE channel header block
	private static final int RH_NSAMP_OFFSET = 4;

	private static final int RHF_RANGE_OFFSET = 26;

	private static final int RHF_DEPTH_OFFSET = 62;

	private final FileOutputStream out;

	public DztWriter(File file) throws FileNotFoundException {
		Check.notNull(file);

		out = new FileOutputStream(file);
	}

	@Override
	public void close() throws IOException {
		out.close();
	}

	public void write(List<DztChannel> channels, int[] traceIndices) throws IOException {
		Check.notEmpty(channels, "No channels to save");
		Check.notNull(traceIndices);
		validateTraceIndices(channels, traceIndices);

		writeHeaders(channels);
		writeChannels(channels, traceIndices);
	}

	private void writeHeaders(List<DztChannel> channels) throws IOException {
		long dataStart = channels.getFirst().getHeader().getDataStart();

		for (DztChannel channel : channels) {
			List<Trace> traces = channel.getTraces();
			int numSamples = !Nulls.isNullOrEmpty(traces)
					? traces.getFirst().numSamples()
					: 0;

			ByteBuffer buffer = createHeaderBuffer(channel.getHeader(), numSamples);
			out.getChannel().write(buffer);
		}

		// Some file variants (e.g. SIR-3000) have extra space between the last
		// channel header and the data area. Pad with zeros so the saved file
		// preserves the same data-start offset that getDataStart() returns.
		long headerBytesWritten = (long) channels.size() * DztHeader.MINHEADSIZE;
		if (headerBytesWritten < dataStart) {
			out.getChannel().write(ByteBuffer.allocate((int) (dataStart - headerBytesWritten)));
		}
	}

	private static ByteBuffer createHeaderBuffer(DztHeader header, int numSamples) {
		Check.notNull(header);

		byte[] bytes = Arrays.copyOf(header.getBuffer().array(), DztHeader.MINHEADSIZE);
		ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

		// header sample count includes the trace index value
		int numHeaderSamples = numSamples + 1;
		buffer.putShort(RH_NSAMP_OFFSET, (short) numHeaderSamples);

		// keep time and depth per sample of the source
		if (header.rh_nsamp > 0) {
			double scale = (double) numHeaderSamples / header.rh_nsamp;
			buffer.putFloat(RHF_RANGE_OFFSET, (float) (header.rhf_range * scale));
			buffer.putFloat(RHF_DEPTH_OFFSET, (float) (header.rhf_depth * scale));
		}
		return buffer;
	}

	private void writeChannels(List<DztChannel> channels, int[] traceIndices) throws IOException {
		int numChannels = channels.size();
		for (int i = 0; i < traceIndices.length; i++) {
			if (Thread.currentThread().isInterrupted()) {
				throw new CancellationException();
			}
			for (int channelIndex = 0; channelIndex < numChannels; channelIndex++) {
				DztChannel channel = channels.get(channelIndex);
				Trace trace = channel.getTraces().get(traceIndices[i]);
				writeSamples(channel.getHeader(), trace, i);
			}
		}
	}

	private static void validateTraceIndices(List<DztChannel> channels, int[] traceIndices) {
		for (int i = 0; i < channels.size(); i++) {
			List<Trace> traces = channels.get(i).getTraces();
			Check.notEmpty(traces, "Channel " + (i + 1) + " has no traces");
			for (int traceIndex : traceIndices) {
				if (traceIndex < 0 || traceIndex >= traces.size()) {
					throw new IllegalArgumentException("Trace " + traceIndex
							+ " is out of channel " + (i + 1)
							+ " size " + traces.size());
				}
			}
		}
	}

	private void writeSamples(DztHeader header, Trace trace, int index) throws IOException {
		SampleCodec codec = SampleCodec.forBitDepth(header.rh_bits);
		int numSamples = trace.numSamples();
		int bufferSize = header.traceSize(numSamples);

		ByteBuffer buffer = ByteBuffer
				.allocate(bufferSize)
				.order(ByteOrder.LITTLE_ENDIAN);

		codec.writeIndex(buffer, index);
		for (int sampleIndex = 0; sampleIndex < numSamples; sampleIndex++) {
			codec.write(buffer, (int) trace.getSample(sampleIndex));
		}

		buffer.position(0);
		out.getChannel().write(buffer);
	}
}
