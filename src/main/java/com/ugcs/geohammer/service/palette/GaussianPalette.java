package com.ugcs.geohammer.service.palette;

import java.awt.Color;

import com.ugcs.geohammer.model.Range;

public final class GaussianPalette implements Palette {

	private static final double SIGMA_RANGE = 2.0;

    private static final double Z_FACTOR = 1.0 / (SIGMA_RANGE * Math.sqrt(2.0));

	private final Spectrum spectrum;

    private final Range range;

    private final Statistics statistics;

	public GaussianPalette(Spectrum spectrum, float[] values, Range range) {
		this.spectrum = spectrum;
        this.range = range;
		this.statistics = Statistics.compute(values, range);
	}

	@Override
	public Color getColor(double value) {
        if (statistics.stddev() == 0) {
            return spectrum.getColor(0.5);
        }
        double z = (value - statistics.mean()) / statistics.stddev();
        double cdf = 0.5 * (1 + erf(z * Z_FACTOR));
        double normalized = Math.clamp(cdf, 0, 1);
        return spectrum.getColor(normalized);
	}

    static double erf(double x) {
        double sign = Math.signum(x);
        x = Math.abs(x);
        double t = 1 / (1 + 0.3275911 * x);
        double v = 1.061405429 * t;
        v = (v - 1.453152027) * t;
        v = (v + 1.421413741) * t;
        v = (v - 0.284496736) * t;
        v = (v + 0.254829592) * t;
        double y = 1 - v * Math.exp(-x * x);
        return sign * y;
    }

	@Override
	public Range getRange() {
		return range;
	}

    private record Statistics(double mean, double stddev) {

        static Statistics compute(float[] values, Range range) {
            double sum = 0;
            double sum2 = 0;
            int count = 0;
            for (float value : values) {
                if (range.contains(value)) {
                    sum += value;
                    sum2 += value * value;
                    count++;
                }
            }
            if (count == 0) {
                return new Statistics(0, 0);
            }
            double mean = sum / count;
            double stddev = Math.sqrt(sum2 / count - mean * mean);
            return new Statistics(mean, stddev);
        }
    }
}
