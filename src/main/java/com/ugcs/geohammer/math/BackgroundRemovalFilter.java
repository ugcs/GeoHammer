package com.ugcs.geohammer.math;

import java.util.List;

import com.ugcs.geohammer.format.gpr.Trace;

public class BackgroundRemovalFilter {
	
	public void subtractProfile(List<Trace> traces, float[] avg) {
        for (Trace trace : traces) {
            int n = Math.min(avg.length, trace.numSamples());
			for (int i = 0; i < n; i++) {
				float value = trace.getSample(i) - avg[i];
				trace.setSample(i, value);
			}
        }
	}

	public float[] computeNoiseProfile(List<Trace> traces, int depth) {
		float[] avg = new float[depth];

        for (Trace trace : traces) {
            int n = Math.min(avg.length, trace.numSamples());
            for (int i = 0; i < n; i++) {
                avg[i] += trace.getSample(i);
            }
        }

		int numTraces = traces.size();
		if (numTraces > 1) {
			for (int i = 0; i < avg.length; i++) {
				avg[i] /= numTraces;
			}
		}

		return avg;
	}
}
