package com.ugcs.gprvisualizer.app.commands;

import java.util.ArrayList;
import java.util.List;

import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.github.thecoldwine.sigrun.common.ext.Trace;
import com.ugcs.gprvisualizer.app.ProgressListener;
import com.ugcs.gprvisualizer.app.TraceCutter;
import com.ugcs.gprvisualizer.dzt.DztFile;
import com.ugcs.gprvisualizer.event.WhatChanged;
import com.ugcs.gprvisualizer.math.HorizontalProfile;
import com.ugcs.gprvisualizer.math.LevelFilter;

public class LevelGround implements Command {

	private final LevelFilter levelFilter;

	public LevelGround(LevelFilter levelFilter) {
		this.levelFilter = levelFilter;
	}

	@Override
	public void execute(SgyFile file, ProgressListener listener) {

		HorizontalProfile hp = file.getGroundProfile();
		if (hp == null) {
			return;
		}
		
		int level = (hp.minDeep + hp.maxDeep) / 2;

		List<Trace> processedTraces = new ArrayList<>();

		for (Trace trace: file.getTraces()) {

			int deep = hp.deep[trace.getIndexInFile()];

			float[] values = trace.getNormValues();
			float[] newValues = new float[values.length];
			int srcStart = Math.max(0, deep - level);
			int dstStart = Math.max(0, level - deep);
			
			System.arraycopy(
				values, srcStart, 
				newValues, dstStart, 
				values.length - Math.abs(deep - level));
			
			Trace newTrace = new Trace(file, trace.getBinHeader(), trace.getHeader(), trace.getOriginalValues(), trace.getLatLon());	

			trace.setNormValues(newValues);
			trace.verticalOffset = level - deep;

			processedTraces.add(newTrace);	
		}

		SgyFile oldFile = generateSgyFileFrom(file, processedTraces);
 		levelFilter.setUndoFiles(List.of(oldFile));
		
		file.setGroundProfile(null);
		file.setUnsaved(true);
	}

	private SgyFile generateSgyFileFrom(SgyFile sourceFile, List<Trace> traces) {
		
		SgyFile sgyFile = sourceFile.copyHeader(); 
		
		sgyFile.setUnsaved(true);
		
		sgyFile.setTraces(traces);
		sgyFile.setFile(sourceFile.getFile());
		
		if (!traces.isEmpty()) {
			int begin = traces.get(0).getIndexInFile();
			int end = traces.get(traces.size() - 1).getIndexInFile();
			sgyFile.setAuxElements(TraceCutter.copyAuxObjects(sourceFile, sgyFile, begin, end));
			/// TODO:
			if (sgyFile instanceof DztFile) {
				DztFile dztfile = (DztFile) sgyFile;
				dztfile.dzg = dztfile.dzg.cut(begin, end);
			}
			///
		}
		
		sgyFile.setGroundProfile(sourceFile.getGroundProfile());

		sgyFile.updateTraces();
		return sgyFile;
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
