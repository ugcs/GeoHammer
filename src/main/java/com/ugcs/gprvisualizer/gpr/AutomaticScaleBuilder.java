package com.ugcs.gprvisualizer.gpr;

import com.github.thecoldwine.sigrun.common.ext.Trace;

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
	public double[][] build() {
		
		for (Trace trace: model.getGprTraces()) {
			analyze(trace.getNormValues());			
		}
		
		double[][] scale = new double[2][maxvalues.length]; 
		
		for (int i = 0; i < maxvalues.length; i++) {
			scale[0][i] = avgvalues[i] / avgcount;
			scale[1][i] = 100 / Math.max(0, maxvalues[i] - scale[0][i]);
		}
		
		return scale;
	}

	private int nrm(int i, int max) {
		return Math.max(0, Math.min(max - 1, i));
	}
	
	public void analyze(float[] values) {
		if (maxvalues.length < values.length) {
			float[] tmp = new float[values.length];
			System.arraycopy(maxvalues, 0, tmp, 0, maxvalues.length);
			maxvalues = tmp;
		}

		if (avgvalues.length < values.length) {
			float[] tmp = new float[values.length];
			System.arraycopy(avgvalues, 0, tmp, 0, avgvalues.length);
			avgvalues = tmp;
		}

		for (int i = 0; i < values.length; i++) {
			maxvalues[i] = Math.max(maxvalues[i], Math.abs(values[i]));
			
			avgvalues[i] += Math.abs(values[i]);
		}
		avgcount++;
	}
	
}
