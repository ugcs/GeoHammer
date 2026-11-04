package com.ugcs.geohammer.math.filter;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LowPassFilterTest {

    private static final double EPSILON = 1e-9;

    @Test
    void lengthOneReturnsInput() {
        List<Number> values = List.of(3.0, -1.0, 7.0, 2.0, 5.0);

        assertValues(values, new LowPassFilter(1).apply(values));
    }

    @Test
    void threePointLineReturnsInput() {
        List<Number> values = List.of(1190.0, 1191.0, 1192.0);

        assertValues(values, new LowPassFilter(100).apply(values));
    }

    @Test
    void constantLineStaysConstant() {
        List<Number> values = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            values.add(50000.0);
        }

        assertValues(values, new LowPassFilter(8).apply(values));
    }

    @Test
    void spikeWithEvenLengthStaysCentred() {
        List<Number> filtered = new LowPassFilter(4).apply(createSpike());

        assertSymmetricAroundSpike(filtered);
    }

    @Test
    void spikeWithOddLengthStaysCentred() {
        List<Number> filtered = new LowPassFilter(5).apply(createSpike());

        assertSymmetricAroundSpike(filtered);
    }

    @Test
    void rampIsKept() {
        List<Number> ramp = createRamp(10);

        assertValues(ramp, new LowPassFilter(4).apply(ramp));
        assertValues(ramp, new LowPassFilter(8).apply(ramp));
    }

    @Test
    void lengthAboveLineSizeKeepsRamp() {
        for (int size : new int[] {2, 10, 11, 500}) {
            List<Number> ramp = createRamp(size);

            assertValues(ramp, new LowPassFilter(1000).apply(ramp));
        }
    }

    @Test
    void missingLastValueGivesNumbers() {
        List<Number> values = createRamp(40);
        values.set(values.size() - 1, null);

        List<Number> filtered = new LowPassFilter(8).apply(values);

        assertEquals(values.size(), filtered.size());
        for (Number value : filtered) {
            assertNotNull(value);
            assertTrue(Double.isFinite(value.doubleValue()));
        }
    }

    @Test
    void lineWithNoValuesStaysEmpty() {
        List<Number> values = Arrays.asList(new Number[10]);

        List<Number> filtered = new LowPassFilter(4).apply(values);

        assertEquals(values.size(), filtered.size());
        for (Number value : filtered) {
            assertNull(value);
        }
    }

    private static List<Number> createRamp(int size) {
        List<Number> ramp = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ramp.add(1190.0 + i);
        }
        return ramp;
    }

    // 11 zeros with a value of 10 at index 5
    private static List<Number> createSpike() {
        List<Number> spike = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            spike.add(i == 5 ? 10.0 : 0.0);
        }
        return spike;
    }

    private static void assertSymmetricAroundSpike(List<Number> filtered) {
        assertEquals(11, filtered.size());
        double sum = 0.0;
        for (int i = 0; i < filtered.size(); i++) {
            sum += filtered.get(i).doubleValue();
            assertTrue(filtered.get(i).doubleValue() <= filtered.get(5).doubleValue());
        }
        for (int d = 1; d <= 5; d++) {
            assertEquals(filtered.get(5 - d).doubleValue(), filtered.get(5 + d).doubleValue(), EPSILON);
        }
        assertTrue(filtered.get(5).doubleValue() < 10.0);
        assertEquals(10.0, sum, EPSILON);
    }

    private static void assertValues(List<Number> expected, List<Number> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i).doubleValue(), actual.get(i).doubleValue(), EPSILON, "index " + i);
        }
    }
}
