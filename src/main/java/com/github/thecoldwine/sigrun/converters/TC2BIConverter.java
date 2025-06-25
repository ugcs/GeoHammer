package com.github.thecoldwine.sigrun.converters;

import com.github.thecoldwine.sigrun.common.ext.Trace;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class TC2BIConverter implements SeismicValuesConverter{

	@Override
	public float[] convert(byte[] bytes) {
		
		
        float[] result = new float[bytes.length / 2];

        ByteBuffer bits = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        
        for (int i = 0; i < result.length; i++) {
        	int val = bits.getShort();
            result[i] = val;
        }
		
		return result;
	}

	public ByteBuffer valuesToByteBuffer(Trace trace) {
		
		ByteBuffer bb = ByteBuffer
				.allocate(trace.numValues() * 2)
				.order(ByteOrder.LITTLE_ENDIAN);
		for (int i = 0; i < trace.numValues(); i++) {
			bb.putShort((short)trace.getValue(i));
		}
		
		return bb;
	}
}
