package com.ugcs.geohammer.format.gpr.segy;

import com.github.thecoldwine.sigrun.converters.IBM360Converter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class IBM360Codec implements SampleCodec {

    private static final int IBM_SIGN_MASK = 0x80000000;

    private static final int IBM_FRAC_MASK = 0x00FFFFFF;

    private static final int FRACTION_BITS = 24;

    private static final int MAX_EXPONENT = 127;

    private final ByteOrder order;

    public IBM360Codec(ByteOrder order) {
        this.order = order;
    }

    @Override
    public float[] decode(byte[] bytes, int offset, int length) {
        float[] result = new float[length / 4];
        ByteBuffer buffer = ByteBuffer.wrap(bytes, offset, length).order(order);
        for (int i = 0; i < result.length; i++) {
            result[i] = Float.intBitsToFloat(IBM360Converter.convert(buffer.getInt()));
        }
        return result;
    }

    @Override
    public byte[] encode(float[] samples, int offset, int length) {
        ByteBuffer buffer = ByteBuffer.allocate(length * 4).order(order);
        for (int i = 0; i < length; i++) {
            buffer.putInt(toIbmBits(samples[offset + i]));
        }
        return buffer.array();
    }

    // IEEE 754 float -> IBM System/360 hex float bits
    static int toIbmBits(float value) {
        if (value == 0f) {
            return 0;
        }
        int sign = 0;
        double v = value;
        if (v < 0) {
            sign = IBM_SIGN_MASK;
            v = -v;
        }
        // normalize so that v is in [1/16, 1): value = v * 16^(exp - 64)
        int exp = 64;
        while (v >= 1.0) {
            v /= 16.0;
            exp++;
        }
        while (v < 1.0 / 16.0) {
            v *= 16.0;
            exp--;
        }
        int fraction = (int) Math.round(v * (1 << FRACTION_BITS));
        if (fraction >= (1 << FRACTION_BITS)) {
            // rounding pushed the fraction past the top hex digit; renormalize
            fraction >>>= 4;
            exp++;
        }
        if (exp <= 0) {
            // underflow - return properly signed zero
            return sign;
        }
        if (exp > MAX_EXPONENT) {
            // overflow - clamp to the largest representable magnitude
            exp = MAX_EXPONENT;
            fraction = IBM_FRAC_MASK;
        }
        return sign | (exp << FRACTION_BITS) | (fraction & IBM_FRAC_MASK);
    }
}
