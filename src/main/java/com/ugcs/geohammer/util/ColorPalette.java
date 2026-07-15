package com.ugcs.geohammer.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.ugcs.geohammer.AppContext;
import javafx.scene.paint.Color;

public class ColorPalette {

    private static final double MIN_SATURATION = 0.55;

    private static final double MAX_SATURATION = 0.95;

    private static final double MIN_LIGHTNESS = 0.20;

    private static final double MAX_LIGHTNESS = 0.80;

    // candidate grid resolution
    private static final int HUE_STEPS = 96;

    private static final int SATURATION_STEPS = 5;

    private static final int LIGHTNESS_STEPS = 13;

    private static final ColorPalette HIGH_CONTRAST = new ColorPalette(96, Color.WHITE, 3.0);

    private static final ColorPalette HIGH_CONTRAST_DARK = new ColorPalette(96, Color.BLACK, 3.0);

    private final Color[] colors;

    public ColorPalette(int numColors, Color background, double minContrast) {
        List<Color> candidates = generateCandidates(background, minContrast);
        Check.condition(candidates.size() >= numColors);
        colors = selectDistinct(candidates, numColors, background);
    }

    public static ColorPalette highContrast() {
        return AppContext.getTheme().dark() ? HIGH_CONTRAST_DARK : HIGH_CONTRAST;
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

    private static List<Color> generateCandidates(Color background, double minContrast) {
        Check.notNull(background);

        List<Color> candidates = new ArrayList<>();
        double dh = 1.0 / HUE_STEPS;
        double ds = (MAX_SATURATION - MIN_SATURATION) / (SATURATION_STEPS - 1);
        double dl = (MAX_LIGHTNESS - MIN_LIGHTNESS) / (LIGHTNESS_STEPS - 1);
        for (int hi = 0; hi < HUE_STEPS; hi++) {
            for (int si = 0; si < SATURATION_STEPS; si++) {
                for (int li = 0; li < LIGHTNESS_STEPS; li++) {
                    Color c = hslToRgb(hi * dh, MIN_SATURATION + si * ds, MIN_LIGHTNESS + li * dl);
                    if (contrastRatio(c, background) >= minContrast) {
                        candidates.add(c);
                    }
                }
            }
        }
        return candidates;
    }

    // greedy farthest-point selection: each pick maximizes the minimum
    // CIEDE2000 distance to the already selected colors, so any prefix
    // of the palette stays well spaced
    private static Color[] selectDistinct(List<Color> candidates, int numColors, Color background) {
        Check.notNull(candidates);
        Check.condition(numColors > 0);

        int n = candidates.size();
        Color[] selected = new Color[numColors];

        // seed with the candidate most distant from background
        Color seed = null;
        double seedDistance = -1;
        for (Color color : candidates) {
            double d = de2000(color, background);
            if (d > seedDistance) {
                seedDistance = d;
                seed = color;
            }
        }
        selected[0] = seed;

        // min distance of ith candidate to all
        // selected colors
        double[] minDistance = new double[n];
        Arrays.fill(minDistance, Double.MAX_VALUE);

        for (int k = 1; k < numColors; k++) {
            Color last = selected[k - 1];
            Color best = null;
            double bestDistance = -1;
            for (int i = 0; i < n; i++) {
                Color color = candidates.get(i);
                double d = de2000(color, last);
                if (d < minDistance[i]) {
                    minDistance[i] = d;
                }
                if (minDistance[i] > bestDistance) {
                    bestDistance = minDistance[i];
                    best = color;
                }
            }
            selected[k] = best;
        }
        return selected;
    }

    private static double hueAngle(double a, double b) {
        double h = Math.toDegrees(Math.atan2(b, a));
        return h < 0 ? h + 360 : h;
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

    private static double[] rgbToLab(Color c) {
        double r = linearize(c.getRed());
        double g = linearize(c.getGreen());
        double b = linearize(c.getBlue());
        double x = (r * 0.4124564 + g * 0.3575761 + b * 0.1804375) / 0.95047;
        double y = r * 0.2126729 + g * 0.7151522 + b * 0.0721750;
        double z = (r * 0.0193339 + g * 0.1191920 + b * 0.9503041) / 1.08883;
        double fx = labF(x);
        double fy = labF(y);
        double fz = labF(z);
        return new double[] {116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }

    private static double labF(double t) {
        return t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16.0 / 116;
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
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    // CIEDE2000 color difference; ~1 is a just noticeable difference,
    // values above ~15 read as clearly distinct colors
    public static double de2000(Color c1, Color c2) {
        Check.notNull(c1);
        Check.notNull(c2);

        double[] lab1 = rgbToLab(c1);
        double[] lab2 = rgbToLab(c2);
        double l1 = lab1[0];
        double a1 = lab1[1];
        double b1 = lab1[2];
        double l2 = lab2[0];
        double a2 = lab2[1];
        double b2 = lab2[2];

        double cab1 = Math.hypot(a1, b1);
        double cab2 = Math.hypot(a2, b2);
        double cabMean = (cab1 + cab2) / 2;
        double g = 0.5 * (1 - Math.sqrt(Math.pow(cabMean, 7) / (Math.pow(cabMean, 7) + Math.pow(25, 7))));
        double a1p = a1 * (1 + g);
        double a2p = a2 * (1 + g);
        double c1p = Math.hypot(a1p, b1);
        double c2p = Math.hypot(a2p, b2);
        double h1p = hueAngle(a1p, b1);
        double h2p = hueAngle(a2p, b2);

        double dlp = l2 - l1;
        double dcp = c2p - c1p;
        double dhpAngle = 0;
        if (c1p * c2p != 0) {
            dhpAngle = h2p - h1p;
            if (dhpAngle > 180) {
                dhpAngle -= 360;
            } else if (dhpAngle < -180) {
                dhpAngle += 360;
            }
        }
        double dhp = 2 * Math.sqrt(c1p * c2p) * Math.sin(Math.toRadians(dhpAngle) / 2);

        double lpMean = (l1 + l2) / 2;
        double cpMean = (c1p + c2p) / 2;
        double hpMean;
        if (c1p * c2p == 0) {
            hpMean = h1p + h2p;
        } else if (Math.abs(h1p - h2p) <= 180) {
            hpMean = (h1p + h2p) / 2;
        } else if (h1p + h2p < 360) {
            hpMean = (h1p + h2p + 360) / 2;
        } else {
            hpMean = (h1p + h2p - 360) / 2;
        }

        double t = 1 - 0.17 * Math.cos(Math.toRadians(hpMean - 30))
                + 0.24 * Math.cos(Math.toRadians(2 * hpMean))
                + 0.32 * Math.cos(Math.toRadians(3 * hpMean + 6))
                - 0.20 * Math.cos(Math.toRadians(4 * hpMean - 63));
        double dTheta = 30 * Math.exp(-Math.pow((hpMean - 275) / 25, 2));
        double rc = 2 * Math.sqrt(Math.pow(cpMean, 7) / (Math.pow(cpMean, 7) + Math.pow(25, 7)));
        double sl = 1 + 0.015 * Math.pow(lpMean - 50, 2) / Math.sqrt(20 + Math.pow(lpMean - 50, 2));
        double sc = 1 + 0.045 * cpMean;
        double sh = 1 + 0.015 * cpMean * t;
        double rt = -Math.sin(Math.toRadians(2 * dTheta)) * rc;

        double fl = dlp / sl;
        double fc = dcp / sc;
        double fh = dhp / sh;
        return Math.sqrt(fl * fl + fc * fc + fh * fh + rt * fc * fh);
    }
}