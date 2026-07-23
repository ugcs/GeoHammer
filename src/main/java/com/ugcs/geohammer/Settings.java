package com.ugcs.geohammer;

public class Settings {

	public static final double MIN_CONTRAST = 0;

	public static final double MAX_CONTRAST = 100;

	private boolean radarMapVisible = true;

	private int maxSamples = 400;

	// top sample of the visible depth window
	private int depthStart = 80;

	// window height in samples
	private int depthHeight = 47;

	private int middleAmplitude;

	private double contrast = 50;

	private boolean autoGain = true;

	private int topGain = 200;

	private int bottomGain = 250;

	private int threshold = 0;

	private int radius = 15;

	public boolean isRadarMapVisible() {
		return radarMapVisible;
	}

	public void setRadarMapVisible(boolean radarMapVisible) {
		this.radarMapVisible = radarMapVisible;
	}

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

	public int getMiddleAmplitude() {
		return middleAmplitude;
	}

	public void setMiddleAmplitude(int middleAmplitude) {
		this.middleAmplitude = middleAmplitude;
	}

	public double getContrast() {
		return contrast;
	}

	public void setContrast(double contrast) {
		this.contrast = contrast;
	}

	public boolean isAutoGain() {
		return autoGain;
	}

	public void setAutoGain(boolean autoGain) {
		this.autoGain = autoGain;
	}

	public int getTopGain() {
		return topGain;
	}

	public void setTopGain(int topGain) {
		this.topGain = topGain;
	}

	public int getBottomGain() {
		return bottomGain;
	}

	public void setBottomGain(int bottomGain) {
		this.bottomGain = bottomGain;
	}

	public int getThreshold() {
		return threshold;
	}

	public void setThreshold(int threshold) {
		this.threshold = threshold;
	}

	public int getRadius() {
		return radius;
	}

	public void setRadius(int radius) {
		this.radius = radius;
	}
}
