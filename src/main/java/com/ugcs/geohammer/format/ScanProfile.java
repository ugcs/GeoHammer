package com.ugcs.geohammer.format;

public class ScanProfile {

	private final double [] intensity;

	public ScanProfile(int size) {
		intensity = new double[size];
	}

	public double getIntensity(int index) {
		return intensity[index];
	}

	public void setIntensity(int index, double value) {
		intensity[index] = value;
	}
}
