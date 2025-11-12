package com.github.thecoldwine.sigrun.converters;

import com.ugcs.geohammer.format.gpr.Trace;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class TC4BIConverter implements SeismicValuesConverter {

	@Override
	public float[] convert(byte[] bytes) {
		
		
        float[] result = new float[bytes.length / 4];

        ByteBuffer bits = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        
        bits.getInt();
        for (int i = 1; i < result.length; i++) {
        	
        	int val = bits.getInt();
        	
            result[i] = val;
            //(val + 33_554_432) / 8192.0f;
        }
		
		return result;
	}
	
	public ByteBuffer valuesToByteBuffer(Trace trace) {
		ByteBuffer bb = ByteBuffer
				.allocate(trace.numSamples() * 4)
				.order(ByteOrder.LITTLE_ENDIAN);
		for (int i = 0; i < trace.numSamples(); i++) {
			//(val + 33_554_432) / 8192.0f
			//int v = (int) (values[i] * 8192.0f) - 33_554_432;
			int v = (int)trace.getSample(i);
			bb.putInt(v);
		}
		return bb;
	}
}
