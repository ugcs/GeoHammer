package com.ugcs.geohammer.service.gpr;

import java.util.List;

import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.model.ProgressListener;
import com.ugcs.geohammer.math.CoordinatesMath;
import com.ugcs.geohammer.model.event.WhatChanged;

public class DistanceCalculator implements Command {

	@Override
	public String getButtonText() {
		return null;
	}

	@Override
	public void execute(TraceFile file, ProgressListener listener) {
		calcDistances(file.getTraces());
	}

	private void calcDistances(List<Trace> traces) {
		for (int i = 1; i < traces.size(); i++) {
			Trace tracePrev = traces.get(i - 1);
			Trace trace = traces.get(i);
			
			if (tracePrev.getLatLon() != null 
					&& trace.getLatLon() != null) {
			
				double dist = CoordinatesMath.measure(
					tracePrev.getLatLon().getLatDgr(), 
					tracePrev.getLatLon().getLonDgr(), 
					trace.getLatLon().getLatDgr(), 
					trace.getLatLon().getLonDgr());
				
				//to cm
				trace.setPrevDist(dist * 100.0);
			} else {
				//some not null value. For example 5 cm.
				trace.setPrevDist(5.0);
			}
		}
		traces.get(0).setPrevDist(traces.get(1).getPrevDist());
	}

    @Override
    public WhatChanged.Change getChange() {
        return null;
    }
}
