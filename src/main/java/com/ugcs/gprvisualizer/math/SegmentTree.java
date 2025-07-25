package com.ugcs.gprvisualizer.math;

import com.ugcs.gprvisualizer.utils.Check;

import java.util.List;

public class SegmentTree {

    private final double[] minTree;
    private final double[] maxTree;
    private final int n;

    public SegmentTree(List<Double> values) {
        Check.notNull(values);

        n = values.size();
        minTree = new double[2 * n];
        maxTree = new double[2 * n];

        // copy values to the second halfs
        for (int i = 0; i < n; i++) {
            Double value = values.get(i);
            minTree[n + i] = value;
            maxTree[n + i] = value;
        }

        // build trees bottom-up
        for (int i = n - 1; i > 0; i--) {
            minTree[i] = Math.min(minTree[2 * i], minTree[2 * i + 1]);
            maxTree[i] = Math.max(maxTree[2 * i], maxTree[2 * i + 1]);
        }
    }

    // [left, right)
    public double queryMin(int left, int right) {
        double min = Double.MAX_VALUE;
        for (left += n, right += n; left < right; left /= 2, right /= 2) {
            if (left % 2 == 1) {
                min = Math.min(min, minTree[left++]);
            }
            if (right % 2 == 1) {
                min = Math.min(min, minTree[--right]);
            }
        }
        return min;
    }

    // [left, right)
    public double queryMax(int left, int right) {
        double max = -Double.MAX_VALUE;
        for (left += n, right += n; left < right; left /= 2, right /= 2) {
            if (left % 2 == 1) {
                max = Math.max(max, maxTree[left++]);
            }
            if (right % 2 == 1) {
                max = Math.max(max, maxTree[--right]);
            }
        }
        return max;
    }
}