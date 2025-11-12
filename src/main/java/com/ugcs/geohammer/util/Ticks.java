package com.ugcs.geohammer.util;

public final class Ticks {

    private Ticks() {
    }

    public static double getPrettyTick(double min, double max, int numTicks) {
        Check.condition(numTicks > 0);

        double range = Math.abs(max - min);
        if (range < 1e-12) {
            return 0.0;
        }

        double roughTick = range / numTicks;
        double base = Math.floor(Math.log10(roughTick));
        double magnitude = Math.pow(10, base);

        // normalize to range [1, 10)
        double normalized = roughTick / magnitude;
        // round to pretty values: 1, 2.5, 5 or 10
        double pretty;
        if (normalized <= 1.0) {
            pretty = 1;
        } else if (normalized <= 2.5) {
            pretty = 2.5;
        } else if (normalized <= 5.0) {
            pretty = 5;
        } else {
            pretty = 10;
        }
        // restore magnitude
        double tick = pretty * magnitude;
        return max >= min ? tick : -tick;
    }
}
