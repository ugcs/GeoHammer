package com.github.thecoldwine.sigrun.common.ext;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ByteBufferHolder implements ByteBufferProducer {

	private Trace trace;
	
	public ByteBufferHolder(Trace trace) {
		
		this.trace = trace;
	}
	
	@Override
	public ByteBuffer read(BlockFile blockFile) throws IOException {

		return valuesToByteBuffer(trace);
	}

	public static ByteBuffer valuesToByteBuffer(Trace trace) {

		ByteBuffer bb = ByteBuffer
				.allocate(trace.numSamples() * 4)
				.order(ByteOrder.LITTLE_ENDIAN);
		
		for (int i = 0; i < trace.numSamples(); i++) {
			bb.putInt((int) trace.getSample(i));
		}
		
		return bb;
	}
	
	
}
