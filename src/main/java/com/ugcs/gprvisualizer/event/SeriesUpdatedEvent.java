package com.ugcs.gprvisualizer.event;

import com.github.thecoldwine.sigrun.common.ext.CsvFile;
import com.ugcs.gprvisualizer.utils.Check;

public class SeriesUpdatedEvent extends BaseEvent {

	private final CsvFile file;

	private final String seriesName;

	private final boolean seriesVisible;

	private final boolean seriesSelected;

	public SeriesUpdatedEvent(Object source, CsvFile file, String seriesName,
			boolean seriesVisible, boolean seriesSelected) {
		super(source);

		Check.notNull(file);
		Check.notEmpty(seriesName);

		this.file = file;
		this.seriesName = seriesName;
		this.seriesVisible = seriesVisible;
		this.seriesSelected = seriesSelected;
	}

	public CsvFile getFile() {
		return file;
	}

	public String getSeriesName() {
		return seriesName;
	}

	public boolean isSeriesVisible() {
		return seriesVisible;
	}

	public boolean isSeriesSelected() {
		return seriesSelected;
	}
}
