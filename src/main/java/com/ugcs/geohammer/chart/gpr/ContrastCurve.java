package com.ugcs.geohammer.chart.gpr;

public final class ContrastCurve {

	// tanh is saturated beyond this many contrast units
	private static final int LIMIT = 4;

	// positive half of the curve over [0, LIMIT] contrast units
	private static final int NUM_BANDS = 4096;

	private static final float[] BANDS = createBands();

	private final float scale;

	public ContrastCurve(double dispersion, double contrast) {
		double calibrationRange = 500.0;
		double saturation = Math.pow(1.08, 140 - contrast) / calibrationRange;
		scale = dispersion > 0
				? (float) ((NUM_BANDS - 1) / (dispersion * saturation * LIMIT))
				: 0f; // every amplitude is middle gray
	}

	private static float[] createBands() {
		float[] bands = new float[NUM_BANDS];
		double step = (double) LIMIT / (NUM_BANDS - 1);
		for (int i = 0; i < bands.length; i++) {
			double x = i * step;
			bands[i] = (float) (0.5 * Math.tanh(x));
		}
		return bands;
	}

	// [0, 1]: 0 is dark
	public float map(float value) {
		float x = value * scale;
		float sign = 1f;
		if (x < 0f) {
			x = -x;
			sign = -1f;
		}
		int i = Math.min((int) x, NUM_BANDS - 1);
		return 0.5f - sign * BANDS[i];
	}

	public int mapToColor(float value) {
		int c = (int) (255 * map(value));
		return (c << 16) + (c << 8) + c;
	}
}
