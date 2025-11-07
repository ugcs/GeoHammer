package com.ugcs.gprvisualizer.event;

import com.github.thecoldwine.sigrun.common.ext.CsvFile;
import com.ugcs.gprvisualizer.utils.Check;

public class SeriesRemovedEvent extends BaseEvent {

	private final CsvFile file;

	private final String seriesName;

	public SeriesRemovedEvent(Object source, CsvFile file, String seriesName) {
		super(source);

		Check.notNull(file);
		Check.notEmpty(seriesName);

		this.file = file;
		this.seriesName = seriesName;
	}

	public CsvFile getFile() {
		return file;
	}

	public String getSeriesName() {
		return seriesName;
	}
}
