package com.ugcs.geohammer.model;

import java.util.Arrays;
import java.util.List;

public enum TraceUnit {
	METERS("m", 1.0, true),
	KILOMETERS("km", 1000.0, true),
	MILES("mi", 1609.344, true),
	FEET("ft", 3.28084, true),
	TIME("time", Double.NaN, false),
	TRACES("traces", Double.NaN, false);

	private final String label;
	private final double metersFactor;
	private final boolean distanceBased;

	TraceUnit(String label, double metersFactor, boolean distanceBased) {
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

	public static TraceUnit getDefault() {
		return METERS;
	}

	public static List<TraceUnit> distanceUnits() {
		return Arrays.stream(values())
				.filter(TraceUnit::isDistanceBased)
				.toList();
	}

	public static double convert(double meters, TraceUnit traceUnit) throws UnsupportedOperationException {
		if (!traceUnit.isDistanceBased()) {
			throw new UnsupportedOperationException("Conversion from meters to " + traceUnit + " is not supported.");
		}
		return meters / traceUnit.metersFactor;
	}
}
