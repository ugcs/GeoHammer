package com.ugcs.gprvisualizer.app.commands;

import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.github.thecoldwine.sigrun.common.ext.Trace;
import com.ugcs.gprvisualizer.app.AppContext;
import com.ugcs.gprvisualizer.app.ProgressListener;
import com.ugcs.gprvisualizer.gpr.ArrayBuilder;
import com.ugcs.gprvisualizer.gpr.Model;
import com.ugcs.gprvisualizer.math.ScanProfile;

public class RadarMapScan implements Command {

	Model model = AppContext.model;
	
	double [][]scaleArray;
	
	public RadarMapScan(ArrayBuilder builder) {
		scaleArray = builder.build();
	}
	
	public void execute(SgyFile file, ProgressListener listener) {

		if (file.amplScan == null) {
			file.amplScan = new ScanProfile(file.size());
		}		
		
		int start = Math.clamp(model.getSettings().getLayer(),
				0, model.getMaxHeightInSamples());
		int finish = Math.clamp(model.getSettings().getLayer() + model.getSettings().hpage,
				0, model.getMaxHeightInSamples());
		
		
		
		for (int i = 0; i < file.size(); i++) {
			
			Trace trace = file.getTraces().get(i);
			
			double alpha = calcAlpha(trace.getNormValues(), trace.edge, start, finish);
			
			file.amplScan.intensity[i] = alpha;
		}		
		
	}

	private double calcAlpha(float[] values, byte[] edge, int start, int finish) {
		double mx = 0;

		start = Math.clamp(start, 0, values.length);
		finish = Math.clamp(finish, 0, values.length);
		
		double additionalThreshold = model.getSettings().autogain ? model.getSettings().threshold : 0;
		
		for (int i = start; i < finish; i++) {
			double threshold = scaleArray[0][i];
			double factor = scaleArray[1][i];		
			
			if (edge[i] != 0) {
				double av = Math.abs(values[i]);
				if (av < additionalThreshold) {
					av = 0;
				}
				double val = Math.max(0, av - threshold) * factor;
				mx = Math.max(mx, val);
			}
		}
		return Math.clamp(mx, 0, 200);
	}

	@Override
	public String getButtonText() {
		return null;
	}

}
