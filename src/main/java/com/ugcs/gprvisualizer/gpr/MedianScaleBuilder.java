package com.ugcs.gprvisualizer.gpr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.github.thecoldwine.sigrun.common.ext.Trace;
import com.github.thecoldwine.sigrun.common.ext.TraceFile;

public class MedianScaleBuilder implements ArrayBuilder {

	private final Model model;
	
	private double[][] scale = null;
	
	public MedianScaleBuilder(Model model) {
		this.model = model;
	}
	
	public void clear() {
		scale = null;
	}
	
	@Override
	public double[][] build(TraceFile file) {
		
		if (scale != null) {
			return scale;
		}

		List<Trace> traces = file.getTraces();
		var maxHeightInSamples = model.getGprChart(file).getField().getMaxHeightInSamples();

		double[][] underconstruction = new double[2][maxHeightInSamples];
		
		for (int smp = 0; smp < maxHeightInSamples; smp++) {

			//all edge values of sample layer
			List<Float> all = new ArrayList<>();

            for (Trace trace : traces) {
                float[] vals = trace.getNormValues();

                if (trace.getEdge()[smp] >= 3) {
                    all.add(Math.abs(smp < vals.length ? vals[smp] : 0));
                }
            }
			
			if (all.isEmpty()) {
				underconstruction[0][smp] = 0;
				underconstruction[1][smp] = 100 / 1000;
			} else {
				Collections.sort(all);
				float median = all.get(all.size() * 35 / 70);
				float principal95 = all.get(all.size() * 98 / 100);
	
				//threshold
				underconstruction[0][smp] = median;
				//kf
				underconstruction[1][smp] = 
						100 / Math.max(0.5, principal95 - median);
			}
		}
		
		scale = underconstruction;
		
		return scale;
	}

	//private int nrm(int i, int max) {
	//	return Math.max(0, Math.min(max - 1, i));
	//}
}
