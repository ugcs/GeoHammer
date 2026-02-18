package com.ugcs.geohammer.service.palette;

import java.awt.Color;

import com.ugcs.geohammer.model.Range;

public final class LinearPalette implements Palette {

	private final Spectrum spectrum;

    private final Range range;

	public LinearPalette(Spectrum spectrum, Range range) {
		this.spectrum = spectrum;
        this.range = range;
	}

	@Override
	public Color getColor(double value) {
        double min = range.getMin();
        double max = range.getMax();
        double normalized = min < max
                ? (Math.clamp(value, min, max) - min) / (max - min)
                : 0.5;
        return spectrum.getColor(normalized);
	}

	@Override
	public Range getRange() {
		return range;
	}
}
