package com.ugcs.geohammer.math.interpolation;

import java.util.Arrays;

public interface Interpolator {

    static Interpolator linear() {
        return new LinearInterpolator();
    }

    static Interpolator spline() {
        return new SplineInterpolator();
    }

    default void interpolate(double[] y) {
        if (y == null) {
            return;
        }
        double[] x = new double[y.length];
        makeIndexed(x);
        interpolate(x, y);
    }

    void interpolate(double[] x, double[] y);

    default int nextPresent(double[] values, int from) {
        if (values == null) {
            return -1;
        }
        for (int i = from; i < values.length; i++) {
            if (isPresent(values[i])) {
                return i;
            }
        }
        return -1;
    }

    default int previousPresent(double[] values, int from) {
        if (values == null) {
            return -1;
        }
        for (int i = from; i >= 0; i--) {
            if (isPresent(values[i])) {
                return i;
            }
        }
        return -1;
    }

    default boolean isPresent(double value) {
        return !Double.isNaN(value);
    }

    default boolean inOrder(double a, double b, int order) {
        int cmp = Double.compare(a, b);
        return cmp == 0 || cmp > 0 && order > 0 || cmp < 0 && order < 0;
    }

    default boolean isMonotonic(double[] values) {
        if (values == null) {
            return true;
        }
        int n = values.length;
        if (n < 2) {
            return true;
        }
        double first = values[0];
        double last = values[n - 1];
        if (!isPresent(first) || !isPresent(last)) {
            return false;
        }
        int order = Double.compare(first, last);
        double previous = first;
        for (int i = 1; i < n; i++) {
            double value = values[i];
            if (!isPresent(value)) {
                return false;
            }
            if (!inOrder(previous, value, order)) {
                return false;
            }
            previous = value;
        }
        return true;
    }

    default void makeIndexed(double[] values) {
        if (values == null) {
            return;
        }
        for (int i = 0; i < values.length; i++) {
            values[i] = i;
        }
    }

    // ensures strictly non-decreasing or non-increasing order of the values,
    // drops values that break the order;
    // interpolates missing gaps;
    // fills missing values on the bounds by the first and last non-empty values
    // in the series;
    // if there are no values, falls back to the counting series;
    default void makeMonotonic(double[] values) {
        if (values == null) {
            return;
        }

        int n = values.length;
        int firstIndex = nextPresent(values, 0);
        if (firstIndex == -1) {
            // no values: fallback to indexed series
            makeIndexed(values);
            return;
        }
        double firstValue = values[firstIndex];
        if (firstIndex > 0) {
            Arrays.fill(values, 0, firstIndex, firstValue);
        }
        int lastIndex = previousPresent(values, n - 1);
        double lastValue = values[lastIndex];
        int order = Double.compare(firstValue, lastValue);

        // fix order
        double previous = firstValue;
        for (int i = firstIndex + 1; i <= lastIndex; i++) {
            double value = values[i];
            if (!isPresent(value)) {
                continue;
            }
            if (!inOrder(previous, value, order)) {
                values[i] = Double.NaN;
            } else {
                previous = value;
            }
        }
        // interpolate gaps
        int l = firstIndex;
        double lValue = firstValue;
        for (int r = firstIndex + 1; r <= lastIndex; r++) {
            double rValue = values[r];
            if (!isPresent(rValue)) {
                continue;
            }
            for (int i = l + 1; i < r; i++) {
                values[i] = lValue + (rValue - lValue) / (r - l) * (i - l);
            }
            l = r;
            lValue = rValue;
        }
        if (l < n - 1) {
            Arrays.fill(values, l + 1, n, lValue);
        }
    }
}
