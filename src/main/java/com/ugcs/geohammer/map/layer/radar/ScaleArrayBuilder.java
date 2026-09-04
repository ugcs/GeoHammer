package com.ugcs.geohammer.map.layer.radar;

import com.ugcs.geohammer.Settings;
import com.ugcs.geohammer.format.TraceFile;

import java.util.Arrays;

public class ScaleArrayBuilder implements ArrayBuilder {

	private final Settings settings;

	private double[][] scale;
	
	public ScaleArrayBuilder(Settings settings) {
		this.settings = settings;
	}
	
	@Override
	public double[][] build(TraceFile file) {
		if (scale != null) {
			return scale;
		}

		int maxSamples = settings.getMaxSamples();
		scale = new double[2][maxSamples];

		double threshold = settings.getThreshold();
		int topGain = settings.getTopGain();
		int bottomGain = settings.getBottomGain();

		// gain increase by sample
		double gainFactor = (double)(bottomGain - topGain) / maxSamples;

		for (int i = 0; i < maxSamples; i++) {
			scale[0][i] = threshold;
			scale[1][i] = (topGain + gainFactor * i) / 10_000.0;
		}

		return scale;
	}

	@Override
	public void clear() {
		scale = null;
	}
}
