package com.ugcs.geohammer.math;

public class LinearInterpolator {
	public static double interpolate(double argument, double leftVal, double rightVal, double leftBorder, double rightBorder) {
		if (rightBorder == leftBorder) {
			return leftVal;
		}
		return leftVal + (rightVal - leftVal) / (rightBorder - leftBorder) * (argument - leftBorder);
	}
}
