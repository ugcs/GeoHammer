package com.ugcs.geohammer.math.interpolation;

import java.util.Arrays;

import com.ugcs.geohammer.util.Check;

public final class SplineInterpolator implements Interpolator {

    private static double tangent(double dx, double dy) {
        if (Double.isNaN(dx) || Double.isNaN(dy)) {
            return 0.0;
        }
        return Math.abs(dx) > 1e-12 ? dy / dx : 0.0;
    }

    private static double tangent(double x, double y, double xp, double yp, double xn, double yn) {
        if (Double.isNaN(xp) || Double.isNaN(yp)) {
            return tangent(xn - x, yn - y);
        }
        if (Double.isNaN(xn) || Double.isNaN(yn)) {
            return tangent(x - xp, y - yp);
        }
        return tangent(xn - xp, yn - yp);
    }

    private static double hermite(double x, double xp, double yp, double xn, double yn, double tp, double tn) {
        double h = xn - xp;
        // coincident xp == xn cannot be interpolated by x; hermite falls back to yp
        double t = Math.abs(h) > 1e-12 ? (x - xp) / h : 0.0;
        double t2 = t * t;
        double t3 = t2 * t;
        double h00 = 2 * t3 - 3 * t2 + 1;
        double h10 = t3 - 2 * t2 + t;
        double h01 = -2 * t3 + 3 * t2;
        double h11 = t3 - t2;
        return h00 * yp
                + h10 * h * tp
                + h01 * yn
                + h11 * h * tn;
    }

    public static double interpolate(double x,
            double xp2, double yp2, double xp1, double yp1,
            double xn1, double yn1, double xn2, double yn2
    ) {
        double tp = tangent(xp1, yp1, xp2, yp2, xn1, yn1);
        double tn = tangent(xn1, yn1, xp1, yp1, xn2, yn2);
        return hermite(x, xp1, yp1, xn1, yn1, tp, tn);
    }

    @Override
    public void interpolate(double[] x, double[] y) {
        if (x == null || y == null) {
            return;
        }
        Check.condition(x.length == y.length);
        makeMonotonic(x);

        // p2 p1 [interpolated] n1 n2
        int p2 = -1;
        int p1 = nextPresent(y, 0);
        if (p1 == -1) {
            return;
        }
        if (p1 > 0) {
            // fill leading gaps
            Arrays.fill(y, 0, p1, y[p1]);
        }
        int n1 = nextPresent(y, p1 + 1);
        int n2 = n1 == -1 ? -1 : nextPresent(y, n1 + 1);

        while (n1 != -1) {
            double xp1 = x[p1];
            double yp1 = y[p1];
            double xn1 = x[n1];
            double yn1 = y[n1];
            if (Math.abs(xp1 - xn1) < 1e-12) {
                // linear segment
                for (int i = p1 + 1; i < n1; i++) {
                    y[i] = yp1 + (yn1 - yp1) / (n1 - p1) * (i - p1);
                }
            } else {
                // spline segment
                // interior gaps filled with cubic Hermite segments
                double xp2 = p2 != -1 ? x[p2] : Double.NaN;
                double yp2 = p2 != -1 ? y[p2] : Double.NaN;
                double xn2 = n2 != -1 ? x[n2] : Double.NaN;
                double yn2 = n2 != -1 ? y[n2] : Double.NaN;

                double tp = tangent(xp1, yp1, xp2, yp2, xn1, yn1);
                double tn = tangent(xn1, yn1, xp1, yp1, xn2, yn2);

                for (int i = p1 + 1; i < n1; i++) {
                    y[i] = hermite(x[i], xp1, yp1, xn1, yn1, tp, tn);
                }
            }
            p2 = p1;
            p1 = n1;
            n1 = n2;
            n2 = n1 == -1 ? -1 : nextPresent(y, n1 + 1);
        }
        // p1 is the last present value, != -1
        if (p1 < y.length - 1) {
            Arrays.fill(y, p1 + 1, y.length, y[p1]);
        }
    }
}
