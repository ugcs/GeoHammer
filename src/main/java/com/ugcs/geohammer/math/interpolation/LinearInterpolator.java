package com.ugcs.geohammer.math.interpolation;

import java.util.Arrays;

import com.ugcs.geohammer.util.Check;
import javafx.geometry.Point2D;

public final class LinearInterpolator implements Interpolator {

    public static Point2D interpolate(Point2D a, Point2D b, double t) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return new Point2D(
                a.getX() + (b.getX() - a.getX()) * t,
                a.getY() + (b.getY() - a.getY()) * t
        );
    }

	public static double interpolate(double x, double xp, double yp, double xn, double yn) {
		if (xn == xp) {
			return yp;
		}
		return yp + (yn - yp) / (xn - xp) * (x - xp);
	}

    @Override
    public void interpolate(double[] x, double[] y) {
        if (x == null || y == null) {
            return;
        }
        Check.condition(x.length == y.length);
        makeMonotonic(x);

        int p = nextPresent(y, 0);
        if (p == -1) {
            return; // no values
        }
        if (p > 0) {
            // fill leading gaps
            Arrays.fill(y, 0, p, y[p]);
        }
        int n = nextPresent(y, p + 1);

        while (n != -1) {
            // fill range from p to n
            double xp = x[p];
            double yp = y[p];
            double xn = x[n];
            double yn = y[n];
            for (int i = p + 1; i < n; i++) {
                y[i] = interpolate(x[i], xp, yp, xn, yn);
            }
            p = n;
            n = nextPresent(y, p + 1);
        }
        // p is the last present value, != -1
        if (p < y.length - 1) {
            Arrays.fill(y, p + 1, y.length, y[p]);
        }
    }
}
