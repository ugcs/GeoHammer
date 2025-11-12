package com.ugcs.geohammer.math;

import com.ugcs.geohammer.util.Check;

import java.util.List;

public class PrefixSum {

    private final double[] prefixSum;
    private final int[] prefixCount;

    public PrefixSum(List<Double> values) {
        Check.notNull(values);

        int n = values.size();
        this.prefixSum = new double[n + 1];
        this.prefixCount = new int[n + 1];

        for (int i = 0; i < n; i++) {
            Double value = values.get(i);
            boolean isValid = value != null && !Double.isNaN(value);

            prefixSum[i + 1] = prefixSum[i] + (isValid ? value : 0.0);
            prefixCount[i + 1] = prefixCount[i] + (isValid ? 1 : 0);
        }
    }

    // [left, right)
    public double querySum(int left, int right) {
        return prefixSum[right] - prefixSum[left];
    }

    // [left, right)
    public int queryCount(int left, int right) {
        return prefixCount[right] - prefixCount[left];
    }

    // [left, right)
    public double queryAvg(int left, int right) {
        double sum = querySum(left, right);
        int count = queryCount(left, right);
        return count > 0 ? sum / count : Double.NaN;
    }
}
