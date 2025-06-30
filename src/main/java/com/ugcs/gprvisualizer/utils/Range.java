package com.ugcs.gprvisualizer.utils;

public class Range {

    private final Number min;

    private final Number max;

    public Range(Number min, Number max) {
        Check.notNull(min);
        Check.notNull(max);

        this.min = min;
        this.max = max;
    }

    public Number getMin() {
        return min;
    }

    public Number getMax() {
        return max;
    }

    public double getCenter() {
        return 0.5 * (min.doubleValue() + max.doubleValue());
    }

    public double getWidth() {
        return max.doubleValue() - min.doubleValue();
    }

    public Range scale(double factor, double centerRatio) {
        factor = Math.max(factor, 0);
        centerRatio = Math.clamp(centerRatio, 0, 1);
        double dw = (factor - 1) * getWidth();
        return new Range(
                min.doubleValue() - centerRatio * dw,
                max.doubleValue() + (1 - centerRatio) * dw
        );
    }
}