package com.ugcs.geohammer.model.event;

public class WhatChanged extends BaseEvent {

	private Change change;

	@Override
	public String toString() {
		return change.name();
	}
	
	public WhatChanged(Object source, Change change) {
		super(source);
		this.change = change;
	}
	
	public boolean isZoom() {
		return change == Change.mapzoom;
	}
	
	public boolean isAdjusting() {
		return change == Change.adjusting;
	}
	
	public boolean isTraceCut() {
		return change == Change.traceCut;
	}
	
	/*public boolean isFileopened() {
		return change == Change.fileopened;
	}*/
	
	public boolean isUpdateButtons() {
		return change == Change.updateButtons;
	}
	
	public boolean isMapscroll() {
		return change == Change.mapscroll;
	}
	
	public boolean isProfilescroll() {
		return change == Change.profilescroll;
	}
	
	public boolean isJustdraw() {
		return change == Change.justdraw;
	}
	
	public boolean isWindowresized() {
		return change == Change.windowresized;
	}
	
	public boolean isTraceValues() {
		return change == Change.traceValues;
	}

	public boolean isCsvDataFiltered() {
		return change == Change.csvDataFiltered;
	}

	public boolean isCsvDataZoom() {
		return change == Change.csvDataZoom;
	}

	public boolean isGriddingRangeChanged() {
		return change == Change.griddingRange;
	}

	public boolean isTraceSelected() {
		return change == Change.traceSelected;
	}

	public enum Change {

		traceValues,
		traceCut,
		windowresized,
		justdraw,
		mapscroll,
		profilescroll,
		mapzoom,

		adjusting,
		updateButtons,
		fileSelected,
		csvDataFiltered,
		csvDataZoom,
		griddingRange,
		traceSelected;
	}
}
