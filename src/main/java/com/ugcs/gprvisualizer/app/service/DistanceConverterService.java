package com.ugcs.gprvisualizer.app.service;

import java.util.Arrays;
import java.util.List;

public class DistanceConverterService {

	public DistanceConverterService() {
		// Default constructor
	}

	public enum Unit {
		METERS("m", 1.0, true),
		KILOMETERS("km", 1000.0, true),
		MILES("mi", 1609.344, true),
		FEET("ft", 0.328084, true),
		SECONDS("s", Double.NaN, false);

		private final String label;
		private final double metersFactor;
		private final boolean distanceBased;

		Unit(String label, double metersFactor, boolean distanceBased) {
			this.label = label;
			this.metersFactor = metersFactor;
			this.distanceBased = distanceBased;
		}

		public String getLabel() {
			return label;
		}

		public boolean isDistanceBased() {
			return distanceBased;
		}

		public static Unit getDefault() {
			return METERS;
		}

		public static List<Unit> distanceUnits() {
			return Arrays.stream(values())
					.filter(Unit::isDistanceBased)
					.toList();
		}
	}

	public static double convert(double meters, Unit unit) {
		if (!unit.isDistanceBased()) {
			throw new UnsupportedOperationException("Conversion from meters to " + unit + " is not supported.");
		}
		return meters / unit.metersFactor;
	}
}