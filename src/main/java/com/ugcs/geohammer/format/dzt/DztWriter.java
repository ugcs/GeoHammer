package com.ugcs.geohammer.format.dzt;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.concurrent.CancellationException;

import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Nulls;

public class DztWriter implements Closeable {

	// rh_nsamp offset within each MINHEADSIZE channel header block
	private static final int RH_NSAMP_OFFSET = 4;

	private final FileOutputStream out;

	public DztWriter(File file) throws FileNotFoundException {
		Check.notNull(file);

		out = new FileOutputStream(file);
	}

	@Override
	public void close() throws IOException {
		out.close();
	}

	public void write(List<DztChannel> channels, IndexRange range) throws IOException {
		Check.notEmpty(channels, "No channels to save");

		writeHeaders(channels);
		writeChannels(channels, range);
	}

	private void writeHeaders(List<DztChannel> channels) throws IOException {
		for (DztChannel channel : channels) {
			List<Trace> traces = channel.getTraces();
			int numSamples = !Nulls.isNullOrEmpty(traces)
					? traces.getFirst().numSamples()
					: 0;

			DztHeader header = channel.getHeader();
			ByteBuffer buffer = header.getBuffer();
			setNumSamples(buffer, numSamples);
			buffer.position(0);
			out.getChannel().write(buffer);
		}
	}

	private void setNumSamples(ByteBuffer buffer, int numSamples) {
		Check.notNull(buffer);

		buffer.position(RH_NSAMP_OFFSET);
		buffer.putShort((short) (numSamples + 1));
	}

	private void writeChannels(List<DztChannel> channels, IndexRange range) throws IOException {
		validateRange(channels, range);

		int numChannels = channels.size();
		for (int traceIndex = range.from(); traceIndex < range.to(); traceIndex++) {
			if (Thread.currentThread().isInterrupted()) {
				throw new CancellationException();
			}
			for (int channelIndex = 0; channelIndex < numChannels; channelIndex++) {
				DztChannel channel = channels.get(channelIndex);
				Trace trace = channel.getTraces().get(traceIndex);
				writeSamples(channel.getHeader(), trace);
			}
		}
	}

	private static void validateRange(List<DztChannel> channels, IndexRange range) {
		for (int i = 0; i < channels.size(); i++) {
			List<Trace> traces = channels.get(i).getTraces();
			Check.notEmpty(traces, "Channel " + (i + 1) + " has no traces");
			if (range.to() > traces.size()) {
				throw new IllegalArgumentException("Range " + range
						+ " exceeds channel " + (i + 1)
						+ " size " + traces.size());
			}
		}
	}

	private void writeSamples(DztHeader header, Trace trace) throws IOException {
		SampleCodec codec = SampleCodec.forBitDepth(header.rh_bits);
		int numSamples = trace.numSamples();
		int bufferSize = header.traceSize(numSamples);

		ByteBuffer buffer = ByteBuffer
				.allocate(bufferSize)
				.order(ByteOrder.LITTLE_ENDIAN);

		codec.write(buffer, trace.getIndex());
		for (int sampleIndex = 0; sampleIndex < numSamples; sampleIndex++) {
			codec.write(buffer, (int) trace.getSample(sampleIndex));
		}

		buffer.position(0);
		out.getChannel().write(buffer);
	}
}
