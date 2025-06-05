package com.ugcs.gprvisualizer.app.commands;

import java.util.List;

import com.github.thecoldwine.sigrun.common.ext.Trace;
import com.github.thecoldwine.sigrun.common.ext.TraceFile;
import com.ugcs.gprvisualizer.app.ProgressListener;
import com.ugcs.gprvisualizer.math.CoordinatesMath;

public class DistCalculator implements Command {

	@Override
	public String getButtonText() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void execute(TraceFile file, ProgressListener listener) {
		calcDistances(file.getTraces());
	}

	private void calcDistances(List<Trace> traces) {
		int zero = 0;
				
		for (int i = 1; i < traces.size(); i++) {
			Trace tracePrev = traces.get(i - 1);
			Trace trace 	= traces.get(i);
			
			if (tracePrev.getLatLon() != null 
					&& trace.getLatLon() != null) {
			
				double dist = CoordinatesMath.measure(
					tracePrev.getLatLon().getLatDgr(), 
					tracePrev.getLatLon().getLonDgr(), 
					trace.getLatLon().getLatDgr(), 
					trace.getLatLon().getLonDgr());
				
				//to cm
				trace.setPrevDist(dist * 100.0);
				
				if (trace.getPrevDist() > 0.0001) {
					//Sout.p("p " + zero + " d " + trace.getPrevDist());
					zero = 0;
				} else {
					zero++;
				}
			} else {
				//some not null value. For example 5 cm.
				trace.setPrevDist(5.0);
			}
		}
		traces.get(0).setPrevDist(traces.get(1).getPrevDist());
	}
}
