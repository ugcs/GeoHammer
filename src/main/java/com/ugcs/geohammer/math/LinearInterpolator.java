package com.ugcs.geohammer.math;

import java.util.AbstractList;
import java.util.List;

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
		DoubleListView listView = new DoubleListView(values);
		interpolateNans(listView);
	}

    public static void interpolateNans(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return;
        }

        int firstIndex = -1;
        for (int i = 0; i < values.size(); i++) {
            if (isPresent(values.get(i))) {
                firstIndex = i;
                break;
            }
        }
        if (firstIndex == -1) {
            return;
        }
        int lastIndex = -1;
        for (int i = values.size() - 1; i >= 0; i--) {
            if (isPresent(values.get(i))) {
                lastIndex = i;
                break;
            }
        }

        // head replicates first
		Double firstValue = values.get(firstIndex);
        for (int i = 0; i < firstIndex; i++) {
			values.set(i, firstValue);
        }
        // tail replicates last
		Double lastValue = values.get(lastIndex);
        for (int i = lastIndex + 1; i < values.size(); i++) {
			values.set(i, lastValue);
        }
        // gaps filled linearly
        int prev = firstIndex;
        for (int i = firstIndex + 1; i <= lastIndex; i++) {
			Double value = values.get(i);
            if (isPresent(value)) {
                for (int j = prev + 1; j < i; j++) {
                    // interpolate
					double interpolatedValue = interpolate(j, prev, i, values.get(prev), value);
					values.set(j, interpolatedValue);
                }
                prev = i;
            }
        }
    }

    private static boolean isPresent(Double value) {
        return value != null && !Double.isNaN(value);
    }

	private static class DoubleListView extends AbstractList<Double> {

		private final double[] data;

		public DoubleListView(double[] data) {
			this.data = data;
		}

		@Override
		public Double get(int index) {
			return data[index];
		}

		@Override
		public Double set(int index, Double element) {
			double old = data[index];
			data[index] = element;
			return old;
		}

		@Override
		public int size() {
			return data.length;
		}
	}
}
