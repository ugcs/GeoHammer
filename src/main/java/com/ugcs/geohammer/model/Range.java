package com.ugcs.geohammer.model;

import com.google.gson.annotations.Expose;

public class Range {

	@Expose
    private final double min;

	@Expose
    private final double max;

    public Range(double min, double max) {
        this.min = min;
        this.max = max;
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getCenter() {
        return 0.5 * (min + max);
    }

    public double getWidth() {
        return max - min;
    }

    public Range scale(double factor, double centerRatio) {
        factor = Math.max(factor, 0);
        centerRatio = Math.clamp(centerRatio, 0, 1);
        double dw = (factor - 1) * getWidth();
        return new Range(
                min - centerRatio * dw,
                max + (1 - centerRatio) * dw
        );
    }

    public Range scaleToWidth(double width, double centerRatio) {
        centerRatio = Math.clamp(centerRatio, 0, 1);
        double dw = width - getWidth();
        return new Range(
                min - centerRatio * dw,
                max + (1 - centerRatio) * dw
        );
    }

    public Range union(Range range) {
        return new Range(
                Math.min(min, range.min),
                Math.max(max, range.max)
        );
    }
}