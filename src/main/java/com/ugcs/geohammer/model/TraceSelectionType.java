package com.ugcs.geohammer.model;

import java.awt.Color;

public enum TraceSelectionType {

	USER(new Color(0xC40000)),
	AUTO(new Color(0xFF6B6B));

	private final Color color;

	TraceSelectionType(Color color) {
		this.color = color;
	}

	public Color getColor() {
		return color;
	}
}
