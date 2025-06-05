package com.ugcs.gprvisualizer.app.commands;

import java.util.List;

import com.github.thecoldwine.sigrun.common.ext.Trace;
import com.github.thecoldwine.sigrun.common.ext.TraceFile;
import com.ugcs.gprvisualizer.app.ProgressListener;
import com.ugcs.gprvisualizer.event.WhatChanged;
import com.ugcs.gprvisualizer.math.BackgroundRemovalFilter;

public class BackgroundNoiseRemover implements Command {

	@Override
	public void execute(TraceFile file, ProgressListener listener) {
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
