package com.ugcs.geohammer.format.gpr.segy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

// 2-byte two's complement integer samples
public class TC2BICodec implements SampleCodec {

    private final ByteOrder order;

    public TC2BICodec(ByteOrder order) {
        this.order = order;
    }

    @Override
    public float[] decode(byte[] bytes, int offset, int length) {
        float[] result = new float[length / 2];
        ByteBuffer buffer = ByteBuffer.wrap(bytes, offset, length).order(order);
        for (int i = 0; i < result.length; i++) {
            result[i] = buffer.getShort();
        }
        return result;
    }

    @Override
    public byte[] encode(float[] samples, int offset, int length) {
        ByteBuffer buffer = ByteBuffer.allocate(length * 2).order(order);
        for (int i = 0; i < length; i++) {
            buffer.putShort((short) samples[offset + i]);
        }
        return buffer.array();
    }
}
