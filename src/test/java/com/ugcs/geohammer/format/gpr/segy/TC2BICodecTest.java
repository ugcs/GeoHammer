package com.ugcs.geohammer.format.gpr.segy;

import org.junit.jupiter.api.Test;

import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class TC2BICodecTest {

    private static final List<ByteOrder> ORDERS = List.of(ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN);

    @Test
    void encode_outOfRange_clampsToShortRange() {
        float[] samples = {40000f, -40000f, Float.MAX_VALUE, -Float.MAX_VALUE};
        float[] expected = {Short.MAX_VALUE, Short.MIN_VALUE, Short.MAX_VALUE, Short.MIN_VALUE};
        for (ByteOrder order : ORDERS) {
            assertArrayEquals(expected, roundTrip(samples, order));
        }
    }

    @Test
    void encode_rangeBounds_keepsValues() {
        float[] samples = {Short.MAX_VALUE, Short.MIN_VALUE};
        for (ByteOrder order : ORDERS) {
            assertArrayEquals(samples, roundTrip(samples, order));
        }
    }

    @Test
    void encode_inRange_truncatesTowardZero() {
        float[] samples = {12.7f, -12.7f, 0f};
        float[] expected = {12f, -12f, 0f};
        for (ByteOrder order : ORDERS) {
            assertArrayEquals(expected, roundTrip(samples, order));
        }
    }

    @Test
    void encode_nan_writesZero() {
        float[] samples = {Float.NaN};
        float[] expected = {0f};
        for (ByteOrder order : ORDERS) {
            assertArrayEquals(expected, roundTrip(samples, order));
        }
    }

    private static float[] roundTrip(float[] samples, ByteOrder order) {
        TC2BICodec codec = new TC2BICodec(order);
        return codec.decode(codec.encode(samples));
    }
}
