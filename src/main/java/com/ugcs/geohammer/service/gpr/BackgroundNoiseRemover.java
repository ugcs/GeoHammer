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
		// Creating snapshot for undo
		FileSnapshot<TraceFile> snapshot = file.createSnapshotWithTraces();
		undoModel.push(new UndoFrame(snapshot));

		BackgroundRemovalFilter brf = new BackgroundRemovalFilter();
		
		List<Trace> lst = file.getTraces();
	
		float[] subteProfile = null; 
				
		if (lst.size() > 1) {
			int deep = file.getMaxSamples();
			
			subteProfile = brf.prepareNoiseProfile(lst, deep);
			brf.subtractProfile(lst, subteProfile);
		}

		file.setUnsaved(true);
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
