package com.ugcs.gprvisualizer.app.commands;

import java.io.File;
import java.util.List;

import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.github.thecoldwine.sigrun.common.ext.Trace;
import com.github.thecoldwine.sigrun.common.ext.TraceFile;
import com.ugcs.gprvisualizer.app.ProgressListener;
import com.ugcs.gprvisualizer.app.auxcontrol.RulerTool;

public class DistancesSmoother implements Command {

	double smoothDist;

	@Override
	public String getButtonText() {	
		return "Smooth distances";
	}

	@Override
	public void execute(TraceFile file, ProgressListener listener) {
		
		smoothDist = RulerTool.distanceVCm(file, 0, 0, file.getMaxSamples() / 2) * 0.25 * 0.5;
		
		smoothDistances(file.getTraces());
	}

	private void smoothDistances(List<Trace> traces) {
		
		int lastindex = traces.size() - 1;
		
		double[] dst = new double[traces.size()]; 
		for (int i = 0; i < dst.length; i++) {
			dst[i] = traces.get(i).getPrevDist();
		}
		
		double[] dst2 = new double[traces.size()]; 
		for (int i = 0; i < dst.length; i++) {
			dst2[i] = avg(dst, i, lastindex);
		}
		dst = dst2;
		
		for (int i = 0; i < dst.length; i++) {
			traces.get(i).setPrevDist(dst[i]);
		}
	}

	int AVG_R = 600;
	protected double avg(double[] dst, int i, int lastindex) {
		double s = 0;
		double c = 0;
		for (
			int j = Math.max(0, i - AVG_R); 
			j <= Math.min(lastindex, i + AVG_R);
			j++) {
			
			s += dst[j];
			c += 1;
		}
		
		return s / c;
	}	
	
	protected double avgDst(double[] dst, int i, int lastindex) {
		double sl = 0;
		double c = 0;
		
		int index = i;
		while (sl < smoothDist && index >= 0 && index <= lastindex) {
			sl += dst[index];
			c += 1;
			
			index--;
		}
		
		double sr = 0;
		index = i + 1;
		while (sr < smoothDist && index >= 0 && index <= lastindex) {
			sr += dst[index];
			c += 1;
			
			index++;
		}
		
		return (sl + sr) / c;
	}
}
