package com.ugcs.gprvisualizer.app.service;

public class DistanceConverterService {
	public enum Unit {
		METERS, KILOMETERS, MILES, FEET
	}

	public static double convert(double meters, Unit unit) {
		return switch (unit) {
			case KILOMETERS -> meters / 1000.0;
			case MILES -> meters / 1609.344;
			case FEET -> meters * 3.28084;
			default -> meters;
		};
	}

	public static String getUnitLabel(Unit unit) {
		return switch (unit) {
			case KILOMETERS -> "km";
			case MILES -> "mi";
			case FEET -> "ft";
			default -> "m";
		};
	}
}