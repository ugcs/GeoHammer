package com.ugcs.gprvisualizer.app.commands;

import com.github.thecoldwine.sigrun.common.ext.TraceFile;
import com.ugcs.gprvisualizer.app.ProgressListener;
import com.ugcs.gprvisualizer.event.WhatChanged;
import com.ugcs.gprvisualizer.gpr.Model;
import com.ugcs.gprvisualizer.math.HorizontalProfile;

public class LevelManualSetter implements Command {

	private final Model model;

	public LevelManualSetter(Model model) {
		this.model = model;
	}

	@Override
	public String getButtonText() {		
		return "Set ground level";
	}

	@Override
	public WhatChanged.Change getChange() {
		return WhatChanged.Change.traceValues;
	}

	@Override
	public void execute(TraceFile file, ProgressListener listener) {
		
		HorizontalProfile levelProfile = new HorizontalProfile(file.size());
		
		int level = model.getProfileField(file).getField().getProfileSettings().getLayer();
		
		for (int i = 0; i < file.size(); i++) {
			levelProfile.deep[i] = level;
		}
		
		levelProfile.finish(file.getTraces());
		
		file.setGroundProfile(levelProfile);		
	}

}
