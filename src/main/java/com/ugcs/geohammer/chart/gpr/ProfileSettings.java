package com.ugcs.geohammer.chart.gpr;

import com.ugcs.geohammer.format.meta.Meta;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.service.palette.SpectrumType;

public class ProfileSettings {

	public static final double MIN_CONTRAST = 0;

	public static final double MAX_CONTRAST = 100;

	public static final double MIN_MAX_GAIN = 0;

	public static final double MAX_MAX_GAIN = 96;

	// top sample of the visible depth window
	private int depthStart = 80;

	// window height in samples
	private int depthHeight = 47;

	private SpectrumType colorScale = SpectrumType.GRAYSCALE;

	private double contrast = 50;

	private double maxGain = 0;

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

	public SpectrumType getColorScale() {
		return colorScale;
	}

	public void setColorScale(SpectrumType colorScale) {
		this.colorScale = colorScale;
	}

	public double getContrast() {
		return contrast;
	}

	public void setContrast(double contrast) {
		this.contrast = contrast;
	}

	public double getMaxGain() {
		return maxGain;
	}

	public void setMaxGain(double maxGain) {
		this.maxGain = maxGain;
	}

	public void readFromMeta(Meta meta) {
		if (meta == null) {
			return;
		}

		IndexRange depthRange = meta.getDepthRange();
		if (depthRange != null) {
			depthStart = depthRange.from();
			depthHeight = depthRange.to() - depthRange.from();
		}

		SpectrumType colorScale = meta.getColorScale();
		if (colorScale != null) {
			this.colorScale = colorScale;
		}

		Double contrast = meta.getContrast();
		if (contrast != null) {
			this.contrast = Math.clamp(contrast, MIN_CONTRAST, MAX_CONTRAST);
		}

		Double maxGain = meta.getMaxGain();
		if (maxGain != null) {
			this.maxGain = Math.clamp(maxGain, MIN_MAX_GAIN, MAX_MAX_GAIN);
		}
	}

	public void writeToMeta(Meta meta) {
		if (meta == null) {
			return;
		}

		meta.setDepthRange(new IndexRange(depthStart, depthStart + depthHeight));
		meta.setColorScale(colorScale);
		meta.setContrast(contrast);
		meta.setMaxGain(maxGain);
	}
}
