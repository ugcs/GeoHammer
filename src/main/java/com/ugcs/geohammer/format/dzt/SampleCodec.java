package com.ugcs.geohammer.format.dzt;

import java.nio.ByteBuffer;
import java.util.Map;

interface SampleCodec {

	Map<Integer, SampleCodec> CODECS = Map.of(
			16, new Sample16Bit(),
			32, new Sample32Bit());

	int read(ByteBuffer buffer);

	void write(ByteBuffer buffer, int value);

	static SampleCodec forBitDepth(int bits) {
		return CODECS.get(bits);
	}

	static int traceBufferSize(int bits, int numSamples) {
		int bytesPerSample = bits >> 3;
		return bytesPerSample * (numSamples + 1);
	}
}

class Sample16Bit implements SampleCodec {

	@Override
	public int read(ByteBuffer buffer) {
		return (buffer.getShort() & 0xFFFF) - 32767;
	}

	@Override
	public void write(ByteBuffer buffer, int value) {
		buffer.putShort((short) (value + 32767));
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
}
