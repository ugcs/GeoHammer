package com.ugcs.geohammer.model.event;

import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.util.Check;

public class SeriesUpdatedEvent extends BaseEvent {

	private final SgyFile file;

	private final String seriesName;

	private final boolean seriesVisible;

	private final boolean seriesSelected;

	public SeriesUpdatedEvent(Object source, SgyFile file, String seriesName,
			boolean seriesVisible, boolean seriesSelected) {
		super(source);

		Check.notNull(file);
		Check.notEmpty(seriesName);

		this.file = file;
		this.seriesName = seriesName;
		this.seriesVisible = seriesVisible;
		this.seriesSelected = seriesSelected;
	}

	public SgyFile getFile() {
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
