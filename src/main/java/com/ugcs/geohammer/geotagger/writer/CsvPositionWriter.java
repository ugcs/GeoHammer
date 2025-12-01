package com.ugcs.geohammer.geotagger.writer;

import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.csv.CsvFile;

public class CsvPositionWriter implements PositionWriter {
	@Override
	public void writePositions(SgyFile file) {
		CsvFile csvFile = (CsvFile) file;
		csvFile.loadFrom(csvFile);
		csvFile.setUnsaved(true);
	}
}
