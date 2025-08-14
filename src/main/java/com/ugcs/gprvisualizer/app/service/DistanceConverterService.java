package com.ugcs.gprvisualizer.app.service;

public class DistanceConverterService {

	public DistanceConverterService() {
		// Default constructor
	}

	public enum Unit {
		METERS("m"),
		KILOMETERS("km"),
		MILES("mi"),
		FEET("ft");

		private final String label;

		Unit(String label) {
			this.label = label;
		}

		public static Unit getDefault() {
			return Unit.METERS;
		}

		public String getLabel() {
			return label;
		}
	}

	public static double convert(double meters, Unit unit) {
		return switch (unit) {
			case KILOMETERS -> meters / 1000.0;
			case MILES -> meters / 1609.344;
			case FEET -> meters * 3.28084;
			case METERS -> meters;
		};
	}
}