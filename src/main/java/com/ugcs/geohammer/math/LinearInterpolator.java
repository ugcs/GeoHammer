package com.ugcs.geohammer.math;

import javafx.geometry.Point2D;

public final class LinearInterpolator {

	private LinearInterpolator() {
	}

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

	// TODO use Range for xRange and yRange
	public static double interpolate(double x, double xMin, double xMax, double yMin, double yMax) {
		if (xMax == xMin) {
			return yMin;
		}
		return yMin + (yMax - yMin) / (xMax - xMin) * (x - xMin);
	}

    public static void interpolateNans(double[] values) {
        if (values == null || values.length == 0) {
            return;
        }

        int first = -1;
        for (int i = 0; i < values.length; i++) {
            if (!Double.isNaN(values[i])) {
                first = i;
                break;
            }
        }
        if (first == -1) {
            return;
        }
        int last = -1;
        for (int i = values.length - 1; i >= 0; i--) {
            if (!Double.isNaN(values[i])) {
                last = i;
                break;
            }
        }

        // head replicates first
        for (int i = 0; i < first; i++) {
            values[i] = values[first];
        }
        // tail replicates last
        for (int i = last + 1; i < values.length; i++) {
            values[i] = values[last];
        }
        // gaps filled linearly
        int prev = first;
        for (int i = first + 1; i <= last; i++) {
            if (!Double.isNaN(values[i])) {
                for (int j = prev + 1; j < i; j++) {
                    // interpolate
                    values[j] = interpolate(j, prev, i, values[prev], values[i]);
                }
                prev = i;
            }
        }
    }
}
