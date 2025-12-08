package com.ugcs.geohammer.math;

public final class LinearInterpolator {

	private LinearInterpolator() {
	}

	// TODO use Range for xRange and yRange
	public static double interpolate(double x, double xMin, double xMax, double yMin, double yMax) {
		if (xMax == xMin) {
			return yMin;
		}
		return yMin + (yMax - yMin) / (xMax - xMin) * (x - xMin);
	}
}
