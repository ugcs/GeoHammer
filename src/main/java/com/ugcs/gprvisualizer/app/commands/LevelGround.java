package com.ugcs.gprvisualizer.app.commands;

import java.util.List;

import com.github.thecoldwine.sigrun.common.ext.Trace;
import com.github.thecoldwine.sigrun.common.ext.TraceFile;
import com.ugcs.gprvisualizer.app.ProgressListener;
import com.ugcs.gprvisualizer.event.WhatChanged;
import com.ugcs.gprvisualizer.math.HorizontalProfile;
import com.ugcs.gprvisualizer.math.LevelFilter;

public class LevelGround implements Command {

	private final LevelFilter levelFilter;

	public LevelGround(LevelFilter levelFilter) {
		this.levelFilter = levelFilter;
	}

	@Override
	public void execute(TraceFile file, ProgressListener listener) {

		HorizontalProfile hp = file.getGroundProfile();
		if (hp == null) {
			return;
		}
		
		int level = (hp.minDeep + hp.maxDeep) / 2;

		// keep undo state
		TraceFile copy = file.copy();
		copy.setGroundProfile(file.getGroundProfile());
		copy.updateTraces();

		levelFilter.setUndoFiles(List.of(copy));

		// update samples
		for (Trace trace: file.getTraces()) {
			int deep = hp.deep[trace.getIndex()];
			int numValues = trace.numSamples();

			int n = Math.max(0, numValues - Math.abs(deep - level));
			float[] buffer = new float[n];

			int srcOffset = Math.max(0, deep - level);
			for (int i = 0; i < n && srcOffset + i < numValues; i++) {
				buffer[i] = trace.getSample(srcOffset + i);
			}

			for (int i = 0; i < numValues; i++) {
				trace.setSample(i, 0f);
			}

			int dstOffset = Math.max(0, level - deep);
			for (int i = 0; i < n && dstOffset + i < numValues; i++) {
				trace.setSample(dstOffset + i, buffer[i]);
			}
		}

		file.setGroundProfile(null);
		file.setUnsaved(true);
	}

	@Override
	public String getButtonText() {
		return "Flatten surface";
		//return "Level ground";
	}

	@Override
	public WhatChanged.Change getChange() {
		return WhatChanged.Change.traceValues;
	}
}
