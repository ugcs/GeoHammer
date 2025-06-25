package com.github.thecoldwine.sigrun.converters;

import com.github.thecoldwine.sigrun.common.ext.Trace;

import java.nio.ByteBuffer;

/**
 * Created by maksenov on 16/01/15.
 */
public interface SeismicValuesConverter {
    int BYTE_SIZE = 8;
    int SHORT_SIZE = Short.SIZE / BYTE_SIZE;
    int INT_SIZE = Integer.SIZE / BYTE_SIZE;
    int FLOAT_SIZE = Float.SIZE / BYTE_SIZE;


    float[] convert(byte[] bytes);
    
    ByteBuffer valuesToByteBuffer(Trace trace);
}
