package com.ugcs.gprvisualizer.math;

import java.util.List;

import com.github.thecoldwine.sigrun.common.ext.Trace;

public class BackgroundRemovalFilter {
	
	public void removeConstantNoise(List<Trace> lst) {
		if (lst.isEmpty()) {
			return;
		}
		int depthIndex = lst.size() > 1 ? 1 : 0;
		float[] avg = prepareNoiseProfile(lst, lst.get(depthIndex).numSamples());
		subtractProfile(lst, avg);
	}

	public void subtractProfile(List<Trace> lst, float[] avg) {
        for (Trace trace : lst) {
            int n = Math.min(avg.length, trace.numSamples());
			for (int i = 0; i < n; i++) {
				float value = trace.getSample(i) - avg[i];
				trace.setSample(i, value);
			}
        }
	}

	public float[] prepareNoiseProfile(List<Trace> lst, int deep) {
		float[] avg = new float[deep];

        for (Trace trace : lst) {
            int n = Math.min(avg.length, trace.numSamples());
            for (int i = 0; i < n; i++) {
                avg[i] += trace.getSample(i);
            }
        }

		ArrayMath.arrayDiv(avg, lst.size());
		return avg;
	}
}
