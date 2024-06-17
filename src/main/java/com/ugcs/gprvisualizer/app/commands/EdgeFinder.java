package com.ugcs.gprvisualizer.app.commands;

import java.util.List;

import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.github.thecoldwine.sigrun.common.ext.Trace;
import com.ugcs.gprvisualizer.app.AppContext;
import com.ugcs.gprvisualizer.app.ProgressListener;
import com.ugcs.gprvisualizer.draw.Change;
import com.ugcs.gprvisualizer.gpr.Model;

public class EdgeFinder implements Command {

	private static double SPEED_SM_NS_VACUUM = 30.0;
	private static double SPEED_SM_NS_SOIL = SPEED_SM_NS_VACUUM / 3.0;
	
	private Model model = AppContext.model;
	
	
	public void execute(SgyFile sgyFile, ProgressListener listener) {
		
		List<Trace> traces = sgyFile.getTraces();
		
		for (int i = 0; i < traces.size(); i++) {
			Trace trace = traces.get(i);
			trace.setFile(sgyFile);
			float[] values = trace.getNormValues();
			trace.edge = new byte[values.length];
			
			int mxind = 0;
			for (int s = 1; s < values.length; s++) {
				
				byte s1 = (byte) Math.signum(values[s - 1]);
				byte s2 = (byte) Math.signum(values[s]);
				
				if (s1 != s2) {
					trace.edge[s] =  s1 > s2 ? (byte) 1 : 2;
					trace.edge[mxind] = (values[mxind]) < 0 ? (byte) 3 : 4;
					mxind = s;
				}
				
				if (Math.abs(values[mxind]) < Math.abs(values[s])) {
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
	public Change getChange() {

		return Change.traceValues;
	}


}
