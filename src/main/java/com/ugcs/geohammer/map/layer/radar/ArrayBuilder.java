package com.ugcs.geohammer.map.layer.radar;

import com.ugcs.geohammer.format.TraceFile;

public interface ArrayBuilder {

	// [2, maxSamples]
	// 0: threshold by depth
	// 1: scale factor by depth
	double[][] build(TraceFile file);
	
	void clear();
}