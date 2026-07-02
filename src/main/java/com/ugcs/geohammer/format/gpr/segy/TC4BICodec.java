package com.ugcs.geohammer.format.gpr.segy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

// 4-byte two's complement integer samples
public class TC4BICodec implements SampleCodec {

    private final ByteOrder order;

    public TC4BICodec(ByteOrder order) {
        this.order = order;
    }

    @Override
    public float[] decode(byte[] bytes, int offset, int length) {
        float[] result = new float[length / 4];
        ByteBuffer buffer = ByteBuffer.wrap(bytes, offset, length).order(order);
        // first sample is skipped by the source format
        buffer.getInt();
        for (int i = 1; i < result.length; i++) {
            result[i] = buffer.getInt();
        }
        return result;
    }

    @Override
    public byte[] encode(float[] samples, int offset, int length) {
        ByteBuffer buffer = ByteBuffer.allocate(length * 4).order(order);
        for (int i = 0; i < length; i++) {
            buffer.putInt((int) samples[offset + i]);
        }
        return buffer.array();
    }
}
