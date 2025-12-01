package com.ugcs.geohammer.geotagger.writer;

import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.TraceFile;

public class TracePositionWriter implements PositionWriter{
	@Override
	public void writePositions(SgyFile file) {
		TraceFile traceFile = (TraceFile) file;
		traceFile.loadFrom(traceFile);
		traceFile.setUnsaved(true);
	}
}
