package com.ugcs.geohammer.format.dzt;

import java.nio.ByteBuffer;
import java.util.Map;

interface SampleCodec {

	Map<Integer, SampleCodec> CODECS = Map.of(
			16, new Sample16Bit(),
			32, new Sample32Bit());

	int read(ByteBuffer buffer);

	void write(ByteBuffer buffer, int value);

	void writeIndex(ByteBuffer buffer, int index);

	static SampleCodec forBitDepth(int bits) {
		SampleCodec codec = CODECS.get(bits);
		if (codec == null) {
			throw new IllegalStateException("Unsupported sample bit depth: " + bits);
		}
		return codec;
	}
}

class Sample16Bit implements SampleCodec {

	// unsigned 16-bit words store values with this offset
	private static final int OFFSET = 32767;

	private static final int MIN_VALUE = -OFFSET;

	private static final int MAX_VALUE = 0xFFFF - OFFSET;

	@Override
	public int read(ByteBuffer buffer) {
		return (buffer.getShort() & 0xFFFF) - OFFSET;
	}

	@Override
	public void write(ByteBuffer buffer, int value) {
		value = Math.clamp(value, MIN_VALUE, MAX_VALUE);
		buffer.putShort((short) (value + OFFSET));
	}

	@Override
	public void writeIndex(ByteBuffer buffer, int index) {
		buffer.putShort((short) (index + OFFSET));
	}
}

class Sample32Bit implements SampleCodec {

	@Override
	public int read(ByteBuffer buffer) {
		return buffer.getInt();
	}

	@Override
	public void write(ByteBuffer buffer, int value) {
		buffer.putInt(value);
	}

	@Override
	public void writeIndex(ByteBuffer buffer, int index) {
		buffer.putInt(index);
	}
}
