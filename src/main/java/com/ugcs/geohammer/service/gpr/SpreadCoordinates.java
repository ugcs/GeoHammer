package com.ugcs.geohammer.service.gpr;

import java.util.List;

import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.model.ProgressListener;
import com.ugcs.geohammer.model.event.WhatChanged;

public class SpreadCoordinates implements Command {
	
	@Override
	public String getButtonText() {
		return "Spread coordinates";
	}

	@Override
	public WhatChanged.Change getChange() {
		return WhatChanged.Change.mapscroll;
	}

	@Override
	public void execute(TraceFile file, ProgressListener listener) {
		
		new DistanceCalculator().execute(file, null);
		
		//Sout.p("prolong");
		prolongDistances(file.getTraces());
		
		new DistanceCalculator().execute(file, null);
		new DistanceSmoother().execute(file, null);
	}

	private void prolongDistances(List<Trace> traces) {
		double[] dst = new double[traces.size()]; 
		for (int i = 0; i < dst.length; i++) {
			dst[i] = traces.get(i).getPrevDist();
		}
		
		double[] dst2 = new double[traces.size()]; 
		
		int lastGoodIndex = 0;
		double thr = 0.0001;
		LatLon ll1 = traces.get(0).getLatLon();
		for (int i = 0; i < dst.length; i++) {
			
			if (dst[i] <= thr) {
				//pass
				//lastZeroIndex = i;
			} else {
				
				double count = (double) (i - lastGoodIndex);
				double partDst = dst[lastGoodIndex] / count;
				
				
				LatLon ll2 = traces.get(lastGoodIndex).getLatLon();
				for (int j = lastGoodIndex; j < i; j++) {
					dst2[j] = partDst;
					
					traces.get(j).setLatLon(new LatLon(
							ll1.getLatDgr() + (ll2.getLatDgr() - ll1.getLatDgr()) * (j - lastGoodIndex) / count, 
							ll1.getLonDgr() + (ll2.getLonDgr() - ll1.getLonDgr()) * (j - lastGoodIndex) / count));
					
				}
				ll1 = ll2;
				thr = dst[i] / 10;
				
				lastGoodIndex = i;
			}
		}
	}

	public static boolean isSpreadingNecessary(TraceFile file) {
		int zeroCount = 0;
		for (Trace trace : file.getTraces()) {
			if (trace.getPrevDist() < 0.001) {
				zeroCount++;				
			}
		}
		return zeroCount > file.numTraces() / 2;
	}

	public static boolean isSpreadingNecessary(List<TraceFile> files) {
		for (TraceFile file : files) {
			if (isSpreadingNecessary(file)) {
				return true;
			}
		}
		return false;
	}
}
