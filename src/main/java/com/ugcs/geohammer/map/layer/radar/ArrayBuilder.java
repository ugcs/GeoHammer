package com.ugcs.geohammer.map.layer.radar;

import com.ugcs.geohammer.format.TraceFile;

public interface ArrayBuilder {

	/**
	 * builds. 
	 * @return [0] - threshold,  [1] - scale  
	 */
	double[][] build(TraceFile file);
	
	void clear();
}