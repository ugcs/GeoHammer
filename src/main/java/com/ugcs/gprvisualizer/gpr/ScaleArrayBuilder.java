package com.ugcs.gprvisualizer.gpr;

import com.github.thecoldwine.sigrun.common.ext.SgyFile;

public class ScaleArrayBuilder implements ArrayBuilder {

	private final Settings settings;

	private double[][] scaleArray = null;
	
	public ScaleArrayBuilder(Settings settings) {
		this.settings = settings;
	}
	
	/* (non-Javadoc)
	 * @see com.ugcs.gprvisualizer.gpr.ArrayBuilder#build()
	 */
	@Override
	public double[][] build(SgyFile file) {
		
		if (scaleArray != null) {
			return scaleArray;
		}
		
		scaleArray = new double[2][settings.maxsamples];
		
		for (int i = 0; i < settings.maxsamples; i++) {
			scaleArray[0][i] = settings.threshold;
			scaleArray[1][i] = (settings.topscale 
					+ (settings.bottomscale - settings.topscale) 
					* i / settings.maxsamples)
					/ 10000.0;
		}
		
		return scaleArray;
	}

	@Override
	public void clear() {
		scaleArray = null;
	}

}
