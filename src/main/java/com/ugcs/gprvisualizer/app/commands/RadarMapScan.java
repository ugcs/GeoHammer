package com.ugcs.gprvisualizer.app.commands;

import com.github.thecoldwine.sigrun.common.ext.Trace;
import com.github.thecoldwine.sigrun.common.ext.TraceFile;
import com.ugcs.gprvisualizer.app.GPRChart;
import com.ugcs.gprvisualizer.app.ProgressListener;
import com.ugcs.gprvisualizer.gpr.ArrayBuilder;
import com.ugcs.gprvisualizer.gpr.Model;
import com.ugcs.gprvisualizer.gpr.Settings;
import com.ugcs.gprvisualizer.math.ScanProfile;

public class RadarMapScan implements Command {

	private final ArrayBuilder scaleBuilder;
	private final Model model;

	public RadarMapScan(ArrayBuilder scaleBuilder, Model model) {
		this.scaleBuilder = scaleBuilder;
		this.model = model;
	}
	
	public void execute(TraceFile file, ProgressListener listener) {

		if (file.amplScan == null) {
			file.amplScan = new ScanProfile(file.size());
		}

		if (model.getProfileField(file) instanceof GPRChart gprChart) {
			var field = gprChart.getField();
			int start = Math.clamp(field.getProfileSettings().getLayer(),
					0, field.getMaxHeightInSamples());

			int finish = Math.clamp(field.getProfileSettings().getLayer() + field.getProfileSettings().hpage,
					0, field.getMaxHeightInSamples());

			for (int i = 0; i < file.size(); i++) {
				Trace trace = file.getTraces().get(i);
				double alpha = calcAlpha(trace.getNormValues(), trace.edge, start, finish, field.getProfileSettings(), scaleBuilder.build(file));
				file.amplScan.intensity[i] = alpha;
			}
		}
	}

	private double calcAlpha(float[] values, byte[] edge, int start, int finish, Settings profileSettings, double[][] scaleArray) {
		double mx = 0;

		start = Math.clamp(start, 0, values.length);
		finish = Math.clamp(finish, 0, values.length);

		double additionalThreshold = profileSettings.autogain ? profileSettings.threshold : 0;
		
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
