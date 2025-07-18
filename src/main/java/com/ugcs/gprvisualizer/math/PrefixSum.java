package com.ugcs.gprvisualizer.math;

import com.ugcs.gprvisualizer.utils.Check;

import java.util.List;

public class PrefixSum {

    private final double[] prefixSum;

    public PrefixSum(List<Double> values) {
        Check.notNull(values);

        int n = values.size();
        this.prefixSum = new double[n + 1];
        for (int i = 0; i < n; i++) {
            prefixSum[i + 1] = prefixSum[i] + values.get(i);
        }
    }

    // [left, right)
    public double queryAvg(int left, int right) {
        double sum = prefixSum[right] - prefixSum[left];
        return sum / (right - left);
    }
}
