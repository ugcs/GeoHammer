package com.ugcs.geohammer.chart.gpr;

public final class ContrastCurve {

	// tanh is saturated beyond this many contrast units
	private static final int LIMIT = 4;

	// bands per half of the curve on each side from zero
	// over [0, LIMIT] contrast units
	private static final int HALF_BANDS = 4095;

	// full curve over [-LIMIT, LIMIT] contrast units, from light to dark
	private static final float[] BANDS = createBands();

	private final float scale;

	public ContrastCurve(double dispersion, double contrast) {
		double calibrationRange = 500.0;
		double saturation = Math.pow(1.08, 140 - contrast) / calibrationRange;
		scale = dispersion > 0
				? (float) (HALF_BANDS / (dispersion * saturation * LIMIT))
				: 0f; // every amplitude is middle gray
	}

	private static float[] createBands() {
		float[] bands = new float[2 * HALF_BANDS + 1];
		double step = (double) LIMIT / HALF_BANDS;
		for (int i = 0; i < bands.length; i++) {
			double x = (i - HALF_BANDS) * step;
			bands[i] = (float) (0.5 - 0.5 * Math.tanh(x));
		}
		return bands;
	}

	// [0, 1]: 0 is dark
	public float map(float value) {
		int band = Math.clamp((int) (value * scale), -HALF_BANDS, HALF_BANDS);
		return BANDS[band + HALF_BANDS];
	}

	public int mapToColor(float value) {
		int c = (int) (255 * map(value));
		return (c << 16) + (c << 8) + c;
	}
}
