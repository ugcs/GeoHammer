package com.ugcs.geohammer.service.palette;

import com.ugcs.geohammer.model.Range;

import java.awt.Color;

public class QuantilePalette implements Palette {

    private static final int NUM_QUANTILES = 31;

    private final Spectrum spectrum;

    private final Range range;

    private final float[] quantiles;

    public QuantilePalette(Spectrum spectrum, float[] sortedValues, Range range) {
        this.spectrum = spectrum;
        this.range = range;
        this.quantiles = buildQuantiles(sortedValues, range);
    }

    // values are expected to be sorted
    private float[] buildQuantiles(float[] values, Range range) {
        int l = lowerBound(values, (float)range.getMin());
        int r = upperBound(values, (float)range.getMax());
        if (l >= r) {
            // less than two values in range
            return new float[0];
        }

        // inv: l < r
        float[] quantiles = new float[NUM_QUANTILES];
        // width of a quantile
        double w = (double)(r - l + 1) / quantiles.length;

        for (int q = 0; q < quantiles.length; q++) {
            double t = l + q * w;
            int i = (int)Math.floor(t);
            if (i == r) {
                i--;
            }
            float v0 = values[i];
            float v1 = values[i + 1];
            double k = t - i;
            // interpolate
            quantiles[q] = (float)(v0 + k * (v1 - v0));
        }
        return quantiles;
    }

    @Override
    public Color getColor(double value) {
        if (quantiles.length < 2) {
            return spectrum.getColor(0.5);
        }
        int i = upperBound(quantiles, (float)value);
        if (i < 0) {
            i = 0;
        }
        double normalized = (double)i / (quantiles.length - 1);
        return spectrum.getColor(normalized);
    }

    @Override
    public Range getRange() {
        return range;
    }

    // first index where a[i] >= key
    private static int lowerBound(float[] a, float key) {
        int l = 0;
        int r = a.length; // exclusive
        while (l < r) {
            int m = (l + r) >>> 1;
            if (a[m] < key) {
                l = m + 1;
            } else {
                r = m;
            }
        }
        return l;
    }

    // last index where a[i] <= key
    private static int upperBound(float[] a, float key) {
        int l = 0;
        int r = a.length; // exclusive
        while (l < r) {
            int m = (l + r) >>> 1;
            if (a[m] <= key) {
                l = m + 1;
            } else {
                r = m;
            }
        }
        return l - 1;
    }
}
