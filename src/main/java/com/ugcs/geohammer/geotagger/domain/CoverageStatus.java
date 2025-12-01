package com.ugcs.geohammer.geotagger.domain;

public enum CoverageStatus {
	NotCovered("Not Covered"),
	PartiallyCovered("Partially Covered"),
	FullyCovered("Fully Covered");

	private final String displayName;

	CoverageStatus(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}
}
