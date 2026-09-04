package com.ugcs.geohammer.service.gpr;

import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.chart.gpr.GPRChart;
import com.ugcs.geohammer.model.ProgressListener;
import com.ugcs.geohammer.map.layer.radar.ArrayBuilder;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.Settings;
import com.ugcs.geohammer.format.ScanProfile;
import com.ugcs.geohammer.model.event.WhatChanged;

public class RadarMapScan implements Command {

	private final ArrayBuilder scaleBuilder;
	private final Model model;

	public RadarMapScan(ArrayBuilder scaleBuilder, Model model) {
		this.scaleBuilder = scaleBuilder;
		this.model = model;
	}
	
	public void execute(TraceFile file, ProgressListener listener) {
		ScanProfile profile = file.getAmplitudeProfile();
		if (profile == null) {
			profile = new ScanProfile(file.numTraces());
			file.setAmplitudeProfile(profile);
		}

		GPRChart gprChart = model.getGprChart(file);
		if (gprChart != null) {
			var field = gprChart.getField();
			int start = Math.clamp(field.getSettings().getDepthStart(),
					0, field.getMaxHeightInSamples());

			int finish = Math.clamp(field.getSettings().getDepthStart() + field.getSettings().getDepthHeight(),
					0, field.getMaxHeightInSamples());

			for (int i = 0; i < file.numTraces(); i++) {
				Trace trace = file.getTraces().get(i);
				double alpha = calcAlpha(trace, start, finish, field.getSettings(), scaleBuilder.build(file));
				profile.setIntensity(i, alpha);
			}
		}
	}

	private double calcAlpha(Trace trace, int start, int finish, Settings profileSettings, double[][] scaleArray) {
		double max = 0;

		start = Math.clamp(start, 0, trace.numSamples());
		finish = Math.clamp(finish, 0, trace.numSamples());

		double additionalThreshold = profileSettings.isAutoGain() ? profileSettings.getThreshold() : 0;
		
		for (int i = start; i < finish; i++) {
			double threshold = scaleArray[0][i];
			double factor = scaleArray[1][i];		
			
			if (trace.getEdge(i).isPeak()) {
				double av = Math.abs(trace.getSample(i));
				if (av < additionalThreshold) {
					av = 0;
				}
				double val = Math.max(0, av - threshold) * factor;
				max = Math.max(max, val);
			}
		}
		return Math.clamp(max, 0, 200);
	}

	@Override
	public String getButtonText() {
		return null;
	}

    @Override
    public WhatChanged.Change getChange() {
        return null;
    }
}
