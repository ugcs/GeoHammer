package com.ugcs.gprvisualizer.gpr;

import com.github.thecoldwine.sigrun.common.ext.TraceFile;

public interface ArrayBuilder {

	/**
	 * builds. 
	 * @return [0] - threshold,  [1] - scale  
	 */
	double[][] build(TraceFile file);
	
	void clear();
}