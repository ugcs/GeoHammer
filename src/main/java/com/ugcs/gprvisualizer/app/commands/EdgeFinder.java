package com.ugcs.gprvisualizer.app.commands;

import java.util.List;

import com.github.thecoldwine.sigrun.common.ext.Trace;
import com.github.thecoldwine.sigrun.common.ext.TraceFile;
import com.ugcs.gprvisualizer.app.AppContext;
import com.ugcs.gprvisualizer.app.ProgressListener;
import com.ugcs.gprvisualizer.event.WhatChanged;
import com.ugcs.gprvisualizer.gpr.Model;

public class EdgeFinder implements Command {

	private static double SPEED_SM_NS_VACUUM = 30.0;
	private static double SPEED_SM_NS_SOIL = SPEED_SM_NS_VACUUM / 3.0;
	
	private Model model = AppContext.model;
	
	
	public void execute(TraceFile sgyFile, ProgressListener listener) {
		
		List<Trace> traces = sgyFile.getTraces();
		
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
