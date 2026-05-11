package com.ugcs.geohammer.service.gpr;

import java.util.List;

import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.format.meta.MetaFile;
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
		if (file.isBackgroundRemoved() || file.getTraces().size() <= 1) {
			return;
		}

		FileSnapshot<TraceFile> snapshot = file.createSnapshotWithTraces();
		undoModel.push(new UndoFrame(snapshot));

		applyFilter(file);
		file.setBackgroundRemoved(true);

		MetaFile meta = file.getMetaFile();
		if (meta != null) {
			meta.setBackgroundRemoved(true);
		}
		file.setUnsaved(true);
	}

	public static boolean applyFromMeta(TraceFile file) {
		if (file.isBackgroundRemoved() || file.getTraces().size() <= 1) {
			return false;
		}
		MetaFile meta = file.getMetaFile();
		if (meta == null || !meta.isBackgroundRemoved()) {
			return false;
		}

		applyFilter(file);
		file.setBackgroundRemoved(true);
		return true;
	}

	private static void applyFilter(TraceFile file) {
		List<Trace> traces = file.getTraces();
		BackgroundRemovalFilter brf = new BackgroundRemovalFilter();
		int deep = file.getMaxSamples();
		float[] noiseProfile = brf.prepareNoiseProfile(traces, deep);
		brf.subtractProfile(traces, noiseProfile);
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
