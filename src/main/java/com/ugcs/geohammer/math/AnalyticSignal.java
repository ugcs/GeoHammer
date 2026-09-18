package com.ugcs.geohammer.math;

import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.model.Range;

public record AnalyticSignal(float[][] magnitudes) {

    public AnalyticSignal {
        Check.notNull(magnitudes);
    }

    public static Range getRange(float[] sortedValues, double percentile) {
        Check.notNull(sortedValues);
        int n = sortedValues.length;
        if (n == 0) {
            return new Range(0f, 0f);
        }
        int k = (int) (percentile * n);
        k = Math.clamp(k, 0, (n - 1) / 2);
        return new Range(sortedValues[k], sortedValues[n - k - 1]);
    }
}
