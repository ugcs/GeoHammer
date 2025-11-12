package com.github.thecoldwine.sigrun.converters;

import com.ugcs.geohammer.format.gpr.Trace;

import java.nio.ByteBuffer;

/**
 * Class that converts IBM360 single precision floating point to IEEE 754 floating point.
 */
public class IBM360Converter implements SeismicValuesConverter {
    public static final int IBM_SIGN_MASK = 0x80000000;
    public static final int IBM_EXPO_MASK = 0x7F000000;
    public static final int IBM_FRAC_MASK = 0x00FFFFFF;

    public static int convert(int bits) {
        int to;

        int sign;
        int exp;
        int fraction;

        final long ibmValue = bits & 0xffffffffl;

        sign = (int) ((ibmValue & IBM_SIGN_MASK) >>> 31);
        exp = (int) ((ibmValue & IBM_EXPO_MASK) >>> 24);
        fraction = (int) ((ibmValue & IBM_FRAC_MASK) << 8);

        if (fraction == 0) { /* short-circuit for zero */
            exp = 0;
            to = (fraction >>> 9) | (exp << 23) | (sign << 31);

            return to;
        }

        exp = (exp << 2) - 130;

        while (fraction > 0) {
            --exp;
            fraction <<= 1;
        }

        if (exp <= 0) {
            if (exp < -24) /* complete underflow - return properly signed zero */
                fraction = 0;
            else /* partial underflow - return denormalized number */
                fraction >>= -exp;
            exp = 0;
        } else if (exp >= 255) { /* overflow - return infinity */
            fraction = 0;
            exp = 255;
        } else { /* just a plain old number - remove the assumed high bit */
            fraction <<= 1;
        }

        to = (fraction >>> 9) | (exp << 23) | (sign << 31);
        return to;

    }

    @Override
    public float[] convert(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("Bytes array cannot be null");
        }

        if ((bytes.length % INT_SIZE) != 0) {
            throw new IllegalArgumentException("Byte array has wrong length");
        }

        float[] result = new float[bytes.length / FLOAT_SIZE];

        ByteBuffer bits = ByteBuffer.wrap(bytes);
        for (int i = 0; i < result.length; i++) {
            result[i] = Float.intBitsToFloat(convert(bits.getInt()));
        }

        return result;
    }
    
    public ByteBuffer valuesToByteBuffer(Trace trace) {
    	throw new RuntimeException("unsuported");
    }
}
