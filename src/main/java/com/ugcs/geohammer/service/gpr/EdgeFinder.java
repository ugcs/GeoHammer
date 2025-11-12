package com.ugcs.geohammer.service.gpr;

import java.util.List;

import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.model.ProgressListener;
import com.ugcs.geohammer.model.event.WhatChanged;

public class EdgeFinder implements Command {

	private static double SPEED_SM_NS_VACUUM = 30.0;
	private static double SPEED_SM_NS_SOIL = SPEED_SM_NS_VACUUM / 3.0;
	
	public void execute(TraceFile traceFile, ProgressListener listener) {
		List<Trace> traces = traceFile.getTraces();
		
		for (int i = 0; i < traces.size(); i++) {
			Trace trace = traces.get(i);

			int mxind = 0;
			for (int s = 1; s < trace.numSamples(); s++) {
				
				byte s1 = (byte) Math.signum(trace.getSample(s - 1));
				byte s2 = (byte) Math.signum(trace.getSample(s));
				
				if (s1 != s2) {
					trace.setEdge(s, s1 > s2 ? (byte) 1 : 2);
					trace.setEdge(mxind, (trace.getSample(mxind)) < 0 ? (byte) 3 : 4);
					mxind = s;
				}
				
				if (Math.abs(trace.getSample(mxind)) < Math.abs(trace.getSample(s))) {
					mxind = s;
				}				
			}			
		}		
	}

	@Override
	public String getButtonText() {
		return "Scan for Edges";
	}

	@Override
	public WhatChanged.Change getChange() {
		return WhatChanged.Change.traceValues;
	}
}
