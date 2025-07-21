package com.ugcs.gprvisualizer.app.commands;

import java.util.List;

import com.github.thecoldwine.sigrun.common.ext.Trace;
import com.github.thecoldwine.sigrun.common.ext.TraceFile;
import com.ugcs.gprvisualizer.app.ProgressListener;
import com.ugcs.gprvisualizer.event.WhatChanged;
import com.ugcs.gprvisualizer.math.HorizontalProfile;

public class LevelGround implements Command {

	@Override
	public void execute(TraceFile file, ProgressListener listener) {
		HorizontalProfile profile = file.getGroundProfile();
		if (profile == null) {
			return;
		}

		int level = profile.getLevel();

		// update samples
		List<Trace> traces = file.getTraces();
		for (int i = 0; i < traces.size(); i++) {
			Trace trace = traces.get(i);
			int depth = profile.getDepth(i);
			int numSamples = trace.numSamples();

			int n = Math.max(0, numSamples - Math.abs(depth - level));
			float[] buffer = new float[n];

			int srcOffset = Math.max(0, depth - level);
			for (int j = 0; j < n && srcOffset + j < numSamples; j++) {
				buffer[j] = trace.getSample(srcOffset + j);
			}

			for (int j = 0; j < numSamples; j++) {
				trace.setSample(j, 0f);
			}

			int dstOffset = Math.max(0, level - depth);
			for (int j = 0; j < n && dstOffset + j < numSamples; j++) {
				trace.setSample(dstOffset + j, buffer[j]);
			}
		}

		file.setGroundProfile(null);
		file.setUnsaved(true);
	}

	@Override
	public String getButtonText() {
		return "Flatten surface";
	}

	@Override
	public WhatChanged.Change getChange() {
		return WhatChanged.Change.traceValues;
	}
}
