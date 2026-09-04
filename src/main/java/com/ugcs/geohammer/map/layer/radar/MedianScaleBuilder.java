package com.ugcs.geohammer.map.layer.radar;

import java.util.Arrays;
import java.util.List;

import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.format.TraceFile;

public class MedianScaleBuilder implements ArrayBuilder {

	private double[][] scale;

	@Override
	public double[][] build(TraceFile file) {
		if (scale != null) {
			return scale;
		}

		List<Trace> traces = file.getTraces();

		int numTraces = traces.size();
		int maxSamples = file.maxSamples();

		double[][] result = new double[2][maxSamples];
		// peaks in all traces at a sample depth, reused at every depth i
		float[] peaks = new float[numTraces];
		for (int i = 0; i < maxSamples; i++) {
			int numPeaks = 0;
            for (Trace trace : traces) {
				if (i >= trace.numSamples()) {
					continue;
				}
                if (trace.getEdge(i).isPeak()) {
					peaks[numPeaks++] = Math.abs(trace.getSample(i));
                }
            }
			
			if (numPeaks == 0) {
				result[0][i] = 0;
				result[1][i] = 100.0 / 1000;
			} else {
				Arrays.sort(peaks, 0, numPeaks);

				int m = numPeaks / 2;
				float median = numPeaks % 2 == 0
						? (peaks[m - 1] + peaks[m]) / 2f
						: peaks[m];
				float upper = peaks[numPeaks * 98 / 100];

				result[0][i] = median;
				result[1][i] = 100 / Math.max(0.5, upper - median);
			}
		}
		
		scale = result;
		return scale;
	}

	@Override
	public void clear() {
		scale = null;
	}
}
