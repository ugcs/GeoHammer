package com.ugcs.geohammer.model;

import com.google.gson.annotations.Expose;

import java.util.Objects;

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

    public boolean contains(double value) {
        return value >= min && value <= max;
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

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Range range)) {
            return false;
        }
        return Double.compare(min, range.min) == 0
                && Double.compare(max, range.max) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(min, max);
    }
}