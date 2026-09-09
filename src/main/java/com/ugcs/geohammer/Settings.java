package com.ugcs.geohammer;

public class Settings {

	public static final double MIN_CONTRAST = 0;

	public static final double MAX_CONTRAST = 100;

	private int maxSamples = 400;

	// top sample of the visible depth window
	private int depthStart = 80;

	// window height in samples
	private int depthHeight = 47;

	private double contrast = 50;

	public int getMaxSamples() {
		return maxSamples;
	}

	public void setMaxSamples(int maxSamples) {
		this.maxSamples = maxSamples;
	}

	public int getDepthStart() {
		return depthStart;
	}

	public void setDepthStart(int depthStart) {
		this.depthStart = depthStart;
	}

	public int getDepthHeight() {
		return depthHeight;
	}

	public void setDepthHeight(int depthHeight) {
		this.depthHeight = depthHeight;
	}

	public double getContrast() {
		return contrast;
	}

	public void setContrast(double contrast) {
		this.contrast = contrast;
	}
}
