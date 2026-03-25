package com.ugcs.geohammer.chart.tool.projection.math;

import com.ugcs.geohammer.util.Check;

public class ContrastCurve {

    private static final int NUM_BANDS = 256;

    private static final float MAX_VALUE = 10_000;

    // positive half in range [0, 0.5]
    private final float[] bands;

    public ContrastCurve(double contrast) {
        double scale = Math.pow(1.08, 140 - contrast);
        bands = buildBands(NUM_BANDS, scale);
    }

    private float[] buildBands(int numBands, double scale) {
        Check.condition(numBands > 1);
        Check.condition(scale > 0);

        float[] bands = new float[numBands];
        double step = MAX_VALUE / (numBands - 1);
        for (int i = 0; i < numBands; i++) {
            bands[i] = (float)(0.5 * Math.tanh(i * step / scale));
        }
        return bands;
    }

    public float map(float value) {
        float sign = 1;
        if (value < 0) {
            value = -value;
            sign = -1;
        }

        value = Math.clamp(value, 0, MAX_VALUE);
        float scaled = (value / MAX_VALUE) * (bands.length - 1);
        int i = (int)Math.floor(scaled); // lower band
        if (i == bands.length - 1) {
            i--;
        }

        float a = bands[i];
        float b = bands[i + 1];
        float t = scaled - i;
        float interpolated = a + t * (b - a);
        return 0.5f + sign * interpolated;
    }
}
