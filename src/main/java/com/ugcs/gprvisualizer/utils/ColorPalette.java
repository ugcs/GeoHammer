package com.ugcs.gprvisualizer.utils;

import javafx.scene.paint.Color;

public class ColorPalette {

    private static final double GOLDEN_RATIO = 2 / (1 + Math.sqrt(5));

    private static final double BASE_SATURATION = 0.85;

    private static final double MIN_SATURATION = 0.55;

    private static final double MAX_SATURATION = 0.95;

    private static final double BASE_LIGHTNESS = 0.5;

    private static final double MIN_LIGHTNESS = 0.20;

    private static final double MAX_LIGHTNESS = 0.80;

    private static final ColorPalette HIGH_CONTRAST = new ColorPalette(64, 3.0);

    private final Color[] colors;

    public ColorPalette(int numColors, double minWhiteContrast) {
        Check.condition(numColors > 0);

        colors = new Color[numColors];
        for (int i = 0; i < numColors; i++) {
            colors[i] = generateColor(i, minWhiteContrast);
        }
    }

    public static ColorPalette highContrast() {
        return HIGH_CONTRAST;
    }

    public Color getColor(int i) {
        return colors[i % colors.length];
    }

    public Color getColor(String s) {
        int h = Strings.nullToEmpty(s).hashCode();
        int n = colors.length;
        int i = h % n;
        if (i < 0) {
            i += n;
        }
        return colors[i];
    }

    private static Color generateColor(int i, double minWhiteContrast) {
        double h = (i * GOLDEN_RATIO) % 1;
        double s = BASE_SATURATION;
        double l = BASE_LIGHTNESS;

        // vary saturation and lightness for more distinction
        double ds = ((i % 2 == 0) ? 0.05 : -0.05) * ((i / 2) % 3);
        s = Math.clamp(s + ds, MIN_SATURATION, MAX_SATURATION);
        double dl = ((i % 5) - 2) * 0.05;
        l = Math.clamp(l + dl, MIN_LIGHTNESS, MAX_LIGHTNESS);

        Color c = hslToRgb(h, s, l);

        // enforce minimum contrast vs white
        int tries = 0;
        int maxTries = 10;
        while (l > MIN_LIGHTNESS
                && tries < maxTries
                && contrastRatio(c, Color.WHITE) < minWhiteContrast) {
            l = Math.clamp(l - 0.05, MIN_LIGHTNESS, MAX_LIGHTNESS);
            c = hslToRgb(h, s, l);
            tries++;
        }

        return c;
    }

    private static Color hslToRgb(double h, double s, double l) {
        double r = l;
        double g = l;
        double b = l;
        if (s > 0) {
            double q = l < 0.5 ? l * (1 + s) : l + s - l * s;
            double p = 2 * l - q;
            r = hueToRgb(p, q, h + 1.0 / 3.0);
            g = hueToRgb(p, q, h);
            b = hueToRgb(p, q, h - 1.0 / 3.0);
        }
        return new Color(r, g, b, 1.0);
    }

    private static double hueToRgb(double p, double q, double t) {
        if (t < 0) {
            t += 1;
        }
        if (t > 1) {
            t -= 1;
        }
        if (t < 1.0 / 6.0) {
            return p + (q - p) * 6 * t;
        }
        if (t < 1.0 / 2.0) {
            return q;
        }
        if (t < 2.0 / 3.0) {
            return p + (q - p) * (2.0 / 3.0 - t) * 6;
        }
        return p;
    }

    private static double contrastRatio(Color a, Color b) {
        double la = relativeLuminance(a);
        double lb = relativeLuminance(b);
        double light = Math.max(la, lb);
        double dark = Math.min(la, lb);
        return (light + 0.05) / (dark + 0.05);
    }

    // WCAG relative luminance
    private static double relativeLuminance(Color c) {
        double r = linearize(c.getRed());
        double g = linearize(c.getGreen());
        double b = linearize(c.getBlue());
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double linearize(double v) {
        return (v <= 0.03928) ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }
}