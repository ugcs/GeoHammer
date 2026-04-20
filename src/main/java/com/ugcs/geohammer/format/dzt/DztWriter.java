package com.ugcs.geohammer.format.dzt;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.util.Check;

class DztWriter {

	// rh_nsamp offset within each MINHEADSIZE channel header block
	private static final int RH_NSAMP_OFFSET = 4;

	void write(File file, File sourceFile, List<DztChannel> channels,
			IndexRange range) throws IOException {
		Check.notNull(file);
		Check.notNull(sourceFile);
		Check.notEmpty(channels, "No channels to save");

		List<ChannelEncoder> encoders = prepareEncoders(channels, range);
		ByteBuffer rawHeaders = readRawHeaders(sourceFile,
				channels.getFirst().getHeader());
		patchNumSamples(rawHeaders, encoders);
		writeBody(file, rawHeaders, encoders, range);
	}

	private record ChannelEncoder(DztChannel channel, SampleCodec codec,
			int numSamples, int traceBufferSize) {

		void writeTrace(FileChannel out, int traceIndex) throws IOException {
			Trace trace = channel.getTraces().get(traceIndex);
			Check.condition(numSamples == trace.numSamples());

			ByteBuffer buffer = ByteBuffer
					.allocate(traceBufferSize)
					.order(ByteOrder.LITTLE_ENDIAN);

			codec.write(buffer, trace.getIndex());
			for (int j = 0; j < numSamples; j++) {
				codec.write(buffer, (int) trace.getSample(j));
			}

			buffer.position(0);
			out.write(buffer);
		}
	}

	private List<ChannelEncoder> prepareEncoders(List<DztChannel> channels,
			IndexRange range) throws IOException {
		List<ChannelEncoder> encoders = new ArrayList<>(channels.size());
		for (int i = 0; i < channels.size(); i++) {
			encoders.add(prepareEncoder(channels.get(i), i, range));
		}
		return encoders;
	}

	private ChannelEncoder prepareEncoder(DztChannel channel,
			int channelIndex, IndexRange range) throws IOException {

		List<Trace> channelTraces = channel.getTraces();
		Check.notEmpty(channelTraces,
				"Channel " + (channelIndex + 1) + " has no traces");
		if (range.to() > channelTraces.size()) {
			throw new IllegalArgumentException("Range " + range
					+ " exceeds channel " + (channelIndex + 1)
					+ " size " + channelTraces.size());
		}

		DztHeader header = channel.getHeader();
		SampleCodec codec = SampleCodec.forBitDepth(header.rh_bits);
		if (codec == null) {
			throw new IOException("Unsupported sample bit depth: " + header.rh_bits);
		}

		int numSamples = channelTraces.getFirst().numSamples();
		int bufferSize = SampleCodec.traceBufferSize(header.rh_bits, numSamples);
		return new ChannelEncoder(channel, codec, numSamples, bufferSize);
	}

	// TODO instead of reloading raw headers from source,
	//  implement serialization of the header structure
	private ByteBuffer readRawHeaders(File sourceFile,
			DztHeader firstHeader) throws IOException {

		long size = DztHeader.getDataPosition(firstHeader);

		ByteBuffer buffer = ByteBuffer
				.allocate((int) size)
				.order(ByteOrder.LITTLE_ENDIAN);

		try (FileInputStream in = new FileInputStream(sourceFile);
			 FileChannel channel = in.getChannel()) {
			channel.read(buffer);
		}
		return buffer;
	}

	private void patchNumSamples(ByteBuffer rawHeaders, List<ChannelEncoder> encoders) {
		// add extra sample for index
		for (int i = 0; i < encoders.size(); i++) {
			rawHeaders.position(i * DztHeader.MINHEADSIZE + RH_NSAMP_OFFSET);
			rawHeaders.putShort((short) (encoders.get(i).numSamples() + 1));
		}
		rawHeaders.position(0);
	}

	private void writeBody(File file, ByteBuffer rawHeaders,
			List<ChannelEncoder> encoders, IndexRange range) throws IOException {

		try (FileOutputStream out = new FileOutputStream(file);
			 FileChannel fileChannel = out.getChannel()) {

			fileChannel.write(rawHeaders);

			for (int traceIndex = range.from(); traceIndex < range.to(); traceIndex++) {
				for (ChannelEncoder encoder : encoders) {
					encoder.writeTrace(fileChannel, traceIndex);
				}
			}
		}
	}
}
