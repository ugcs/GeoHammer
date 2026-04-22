package com.ugcs.geohammer.format.dzt;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.concurrent.CancellationException;

import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.util.Check;

public class DztWriter {

	// rh_nsamp offset within each MINHEADSIZE channel header block
	private static final int RH_NSAMP_OFFSET = 4;

	public void write(File file, File sourceFile, List<DztChannel> channels,
			IndexRange range) throws IOException {
		Check.notNull(file);
		Check.notNull(sourceFile);
		Check.notEmpty(channels, "No channels to save");

		try (FileOutputStream fileOut = new FileOutputStream(file);
			 FileChannel out = fileOut.getChannel()) {
			writeHeaders(out, sourceFile, channels);
			writeChannels(out, channels, range);
		}
	}

	// TODO instead of reloading raw headers from source,
	//  implement serialization of the header structure
	private void writeHeaders(FileChannel out, File sourceFile,
			List<DztChannel> channels) throws IOException {

		ByteBuffer rawHeaders = readRawHeaders(sourceFile,
				channels.getFirst().getHeader());
		patchNumSamples(rawHeaders, channels);
		out.write(rawHeaders);
	}

	private ByteBuffer readRawHeaders(File sourceFile,
			DztHeader firstHeader) throws IOException {

		long size = firstHeader.getDataAreaStart();
		ByteBuffer buffer = ByteBuffer
				.allocate((int) size)
				.order(ByteOrder.LITTLE_ENDIAN);

		try (FileInputStream in = new FileInputStream(sourceFile);
			 FileChannel channel = in.getChannel()) {
			channel.read(buffer);
		}
		return buffer;
	}

	private void patchNumSamples(ByteBuffer rawHeaders, List<DztChannel> channels) {
		// add extra sample for index
		for (int i = 0; i < channels.size(); i++) {
			int numSamples = channels.get(i).getTraces().getFirst().numSamples();
			rawHeaders.position(i * DztHeader.MINHEADSIZE + RH_NSAMP_OFFSET);
			rawHeaders.putShort((short) (numSamples + 1));
		}
		rawHeaders.position(0);
	}

	private void writeChannels(FileChannel out, List<DztChannel> channels,
			IndexRange range) throws IOException {

		validateRange(channels, range);

		int numChannels = channels.size();
		for (int traceIndex = range.from(); traceIndex < range.to(); traceIndex++) {
			if (Thread.currentThread().isInterrupted()) {
				throw new CancellationException();
			}
			for (int channelIndex = 0; channelIndex < numChannels; channelIndex++) {
				DztChannel channel = channels.get(channelIndex);
				Trace trace = channel.getTraces().get(traceIndex);
				writeSamples(out, channel.getHeader(), trace);
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

	private void writeSamples(FileChannel out, DztHeader header,
			Trace trace) throws IOException {

		SampleCodec codec = SampleCodec.forBitDepth(header.rh_bits);
		if (codec == null) {
			throw new IOException("Unsupported sample bit depth: " + header.rh_bits);
		}
		int numSamples = trace.numSamples();
		int bufferSize = SampleCodec.traceBufferSize(header.rh_bits, numSamples);

		ByteBuffer buffer = ByteBuffer
				.allocate(bufferSize)
				.order(ByteOrder.LITTLE_ENDIAN);

		codec.write(buffer, trace.getIndex());
		for (int j = 0; j < numSamples; j++) {
			codec.write(buffer, (int) trace.getSample(j));
		}

		buffer.position(0);
		out.write(buffer);
	}
}
