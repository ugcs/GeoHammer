package com.ugcs.gprvisualizer.app.commands;

import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.github.thecoldwine.sigrun.common.ext.TraceFile;
import com.ugcs.gprvisualizer.app.ProgressListener;
import com.ugcs.gprvisualizer.event.WhatChanged;
import com.ugcs.gprvisualizer.math.LevelFilter;

import java.io.File;

public class LevelClear implements Command {

	private final LevelFilter levelFilter;

	public LevelClear(LevelFilter levelFilter) {
		this.levelFilter = levelFilter;
	}

	@Override
	public void execute(TraceFile file, ProgressListener listener) {
		if (levelFilter.getUndoFiles() == null) {
			return;
		}

		TraceFile undoFile = levelFilter.getUndoFiles().getFirst();
		file.setTraces(undoFile.getTraces());
		levelFilter.setUndoFiles(null);

		file.setGroundProfile(undoFile.getGroundProfile());
	}

	@Override
	public String getButtonText() {
		return "Undo flattening";
	}

	@Override
	public WhatChanged.Change getChange() {
		return WhatChanged.Change.traceValues;
	}
}
