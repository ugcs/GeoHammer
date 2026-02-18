package com.ugcs.geohammer.model.event;

import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.map.layer.GridLayer;
import com.ugcs.geohammer.util.Check;

public class GridUpdatedEvent extends BaseEvent {

	private final SgyFile file;

	private final GridLayer.Grid grid;

	public GridUpdatedEvent(Object source, SgyFile file, GridLayer.Grid grid) {
		super(source);

		Check.notNull(file);

		this.file = file;
		this.grid = grid;
	}

	public SgyFile getFile() {
		return file;
	}

	public GridLayer.Grid getGrid() {
		return grid;
	}
}
