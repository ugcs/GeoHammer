package com.ugcs.geohammer.util;

public final class Formats {

    private Formats() {
    }

    public static int getPreferredFractionDigits(Number min, Number max) {
        double diff = Math.abs(max.doubleValue() - min.doubleValue());
        int log10 = (int)Math.ceil(Math.log10(diff));
        // 0 for diff > 100, max 3 digits
        return Math.clamp(3 - log10, 0, 3);
    }

    public static String prettyForRange(Number number, Number min, Number max) {
        int numFractionDigits = getPreferredFractionDigits(min, max);
        return Text.formatNumber(number, numFractionDigits);
    }
}
