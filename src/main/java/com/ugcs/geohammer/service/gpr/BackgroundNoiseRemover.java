package com.ugcs.geohammer.service.gpr;

import java.util.List;

import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.model.ProgressListener;
import com.ugcs.geohammer.model.undo.FileSnapshot;
import com.ugcs.geohammer.model.undo.UndoFrame;
import com.ugcs.geohammer.model.undo.UndoModel;
import com.ugcs.geohammer.model.event.WhatChanged;
import com.ugcs.geohammer.math.BackgroundRemovalFilter;

public class BackgroundNoiseRemover implements Command {

	private final UndoModel undoModel;

	public BackgroundNoiseRemover(UndoModel undoModel) {
		this.undoModel = undoModel;
	}

	@Override
	public void execute(TraceFile file, ProgressListener listener) {
		if (file.getTraces().size() <= 1) {
			return;
		}

		if (undoModel != null) {
			FileSnapshot<TraceFile> snapshot = file.createSnapshotWithTraces();
			undoModel.push(new UndoFrame(snapshot));
		}

		List<Trace> traces = file.getTraces();
		BackgroundRemovalFilter brf = new BackgroundRemovalFilter();
		int deep = file.getMaxSamples();
		float[] noiseProfile = brf.prepareNoiseProfile(traces, deep);
		brf.subtractProfile(traces, noiseProfile);

		if (undoModel != null) {
			file.setUnsaved(true);
		}
	}

	@Override
	public String getButtonText() {
		return "Remove background";
	}

	@Override
	public WhatChanged.Change getChange() {
		return WhatChanged.Change.traceValues;
	}
}
