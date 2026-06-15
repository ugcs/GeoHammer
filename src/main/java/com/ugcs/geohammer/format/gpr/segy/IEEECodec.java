package com.ugcs.geohammer.format.gpr.segy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class IEEECodec implements SampleCodec {

    private static final float SCALE = 10000f;

    private final ByteOrder order;

    public IEEECodec(ByteOrder order) {
        this.order = order;
    }

    @Override
    public float[] decode(byte[] bytes, int offset, int length) {
        float[] result = new float[length / 4];
        FloatBuffer buffer = ByteBuffer.wrap(bytes, offset, length).order(order).asFloatBuffer();
        for (int i = 0; i < result.length; i++) {
            result[i] = buffer.get() * SCALE;
        }
        return result;
    }

    @Override
    public byte[] encode(float[] samples, int offset, int length) {
        ByteBuffer buffer = ByteBuffer.allocate(length * 4).order(order);
        for (int i = 0; i < length; i++) {
            buffer.putFloat(samples[offset + i] / SCALE);
        }
        return buffer.array();
    }
}
