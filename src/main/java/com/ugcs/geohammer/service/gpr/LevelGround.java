package com.ugcs.geohammer.service.gpr;

import java.util.List;

import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.model.ProgressListener;
import com.ugcs.geohammer.model.event.WhatChanged;
import com.ugcs.geohammer.format.HorizontalProfile;

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
