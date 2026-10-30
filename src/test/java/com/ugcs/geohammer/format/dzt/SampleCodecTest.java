package com.ugcs.geohammer.format.dzt;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SampleCodecTest {

    private static final int BITS_16 = 16;

    private static final int BITS_32 = 32;

    private static final int BUFFER_SIZE = 4;

    // 16-bit samples are stored with this offset
    private static final int SAMPLE_OFFSET = 32767;

    private static final int SAMPLE_16_MIN = -32767;

    private static final int SAMPLE_16_MAX = 32768;

    @Test
    void write16_aboveRange_clampsToMax() {
        assertEquals(SAMPLE_16_MAX, roundTrip(BITS_16, 40000));
        assertEquals(SAMPLE_16_MAX, roundTrip(BITS_16, Integer.MAX_VALUE));
    }

    @Test
    void write16_belowRange_clampsToMin() {
        assertEquals(SAMPLE_16_MIN, roundTrip(BITS_16, -40000));
        assertEquals(SAMPLE_16_MIN, roundTrip(BITS_16, Integer.MIN_VALUE));
    }

    @Test
    void write16_inRange_keepsValues() {
        assertEquals(SAMPLE_16_MAX, roundTrip(BITS_16, SAMPLE_16_MAX));
        assertEquals(SAMPLE_16_MIN, roundTrip(BITS_16, SAMPLE_16_MIN));
        assertEquals(0, roundTrip(BITS_16, 0));
        assertEquals(-1234, roundTrip(BITS_16, -1234));
    }

    @Test
    void writeIndex16_largeIndex_wrapsAsBefore() {
        int index = 40000;

        ByteBuffer expected = createBuffer();
        expected.putShort((short) (index + SAMPLE_OFFSET));

        ByteBuffer actual = createBuffer();
        SampleCodec.forBitDepth(BITS_16).writeIndex(actual, index);

        assertArrayEquals(expected.array(), actual.array());
    }

    @Test
    void write32_keepsValues() {
        assertEquals(40000, roundTrip(BITS_32, 40000));
        assertEquals(Integer.MIN_VALUE, roundTrip(BITS_32, Integer.MIN_VALUE));
    }

    @Test
    void writeIndex32_keepsIndex() {
        SampleCodec codec = SampleCodec.forBitDepth(BITS_32);
        ByteBuffer buffer = createBuffer();
        codec.writeIndex(buffer, 40000);
        buffer.flip();
        assertEquals(40000, codec.read(buffer));
    }

    private static int roundTrip(int bits, int value) {
        SampleCodec codec = SampleCodec.forBitDepth(bits);
        ByteBuffer buffer = createBuffer();
        codec.write(buffer, value);
        buffer.flip();
        return codec.read(buffer);
    }

    private static ByteBuffer createBuffer() {
        return ByteBuffer.allocate(BUFFER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
    }
}
