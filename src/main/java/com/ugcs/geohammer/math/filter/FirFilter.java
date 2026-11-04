package com.ugcs.geohammer.math.filter;

import com.ugcs.geohammer.util.Check;

import java.util.ArrayList;
import java.util.List;

public class FirFilter {

    // the kernel has 2 * halfWidth + 1 taps centred at halfWidth
    private final double[] kernel;

    public FirFilter(int order) {
        Check.condition(order >= 1);

        this.kernel = createKernel(order);
    }

    private static double[] createKernel(int order) {
        Check.condition(order >= 1);

        int halfWidth = order / 2;
        int size = 2 * halfWidth + 1;
        double[] kernel = new double[size];
        double[] window = createHammingWindow(size);

        // normalized cut-off frequency
        double cutoff = 2.0 / order;
        for (int i = 0; i < size; i++) {
            int n = i - halfWidth;
            double sinc = n == 0
                    ? 1.0
                    : Math.sin(Math.PI * cutoff * n) / (Math.PI * cutoff * n);
            kernel[i] = cutoff * sinc * window[i];
        }
        return kernel;
    }

    private static double[] createHammingWindow(int size) {
        Check.condition(size > 0);

        double[] window = new double[size];
        if (size == 1) {
            window[0] = 1.0;
            return window;
        }
        for (int i = 0; i < size; i++) {
            window[i] = 0.54 - 0.46 * Math.cos(2 * Math.PI * i / (size - 1));
        }
        return window;
    }

    public List<Number> filterList(List<Number> values) {
        if (values == null || values.size() <= 1) {
            return values;
        }

        double[] padded = padValues(values);
        List<Number> filtered = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            double sum = 0.0;
            double weight = 0.0;
            for (int k = 0; k < kernel.length; k++) {
                double value = padded[i + k];
                if (Double.isFinite(value)) {
                    sum += kernel[k] * value;
                    weight += kernel[k];
                }
            }
            filtered.add(weight > 0.0 ? sum / weight : null);
        }
        return filtered;
    }

    // extends values by halfWidth values at each end, mirrored around
    // the first and last available values (point reflection), so that
    // linear trends continue through the ends; missing values are NaN
    private double[] padValues(List<Number> values) {
        Check.notNull(values);

        int n = values.size();
        // kernel half-width
        int halfWidth = kernel.length / 2;
        double[] padded = new double[n + 2 * halfWidth];
        for (int i = 0; i < n; i++) {
            Number value = values.get(i);
            padded[halfWidth + i] = value != null ? value.doubleValue() : Double.NaN;
        }

        double first = Double.NaN;
        for (int i = 0; i < n && !Double.isFinite(first); i++) {
            first = padded[halfWidth + i];
        }
        double last = Double.NaN;
        for (int i = n - 1; i >= 0 && !Double.isFinite(last); i--) {
            last = padded[halfWidth + i];
        }

        // both ends are filled outwards together, so when halfWidth exceeds
        // the data size, padding is mirrored again from the opposite end
        for (int i = 1; i <= halfWidth; i++) {
            padded[halfWidth - i] = 2 * first - padded[halfWidth + i];
            padded[halfWidth + n - 1 + i] = 2 * last - padded[halfWidth + n - 1 - i];
        }
        return padded;
    }
}
