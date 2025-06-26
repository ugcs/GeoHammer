package com.ugcs.gprvisualizer.gpr;

import com.github.thecoldwine.sigrun.common.ext.Trace;
import com.github.thecoldwine.sigrun.common.ext.TraceFile;

public class AutomaticScaleBuilder implements ArrayBuilder {

	private final Model model;
	private float[] maxvalues = new float[1];
	private float[] avgvalues = new float[1];
	private double avgcount = 0;
	
	public AutomaticScaleBuilder(Model model) {
		this.model = model;
	}
	
	public void clear() {
		maxvalues = new float[1];
		avgvalues = new float[1];
		avgcount = 0;		
	}
	
	@Override
	public double[][] build(TraceFile file) {
		
		for (Trace trace: file.getTraces()) {
			analyze(trace);
		}
		
		double[][] scale = new double[2][maxvalues.length]; 
		
		for (int i = 0; i < maxvalues.length; i++) {
			scale[0][i] = avgvalues[i] / avgcount;
			scale[1][i] = 100 / Math.max(0, maxvalues[i] - scale[0][i]);
		}
		
		return scale;
	}

	public void analyze(Trace trace) {
		if (maxvalues.length < trace.numSamples()) {
			float[] tmp = new float[trace.numSamples()];
			System.arraycopy(maxvalues, 0, tmp, 0, maxvalues.length);
			maxvalues = tmp;
		}

		if (avgvalues.length < trace.numSamples()) {
			float[] tmp = new float[trace.numSamples()];
			System.arraycopy(avgvalues, 0, tmp, 0, avgvalues.length);
			avgvalues = tmp;
		}

		for (int i = 0; i < trace.numSamples(); i++) {
			maxvalues[i] = Math.max(maxvalues[i], Math.abs(trace.getSample(i)));
			avgvalues[i] += Math.abs(trace.getSample(i));
		}
		avgcount++;
	}
}
