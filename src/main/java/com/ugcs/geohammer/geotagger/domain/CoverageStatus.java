package com.ugcs.geohammer.geotagger.domain;

import java.util.ArrayList;
import java.util.List;

import com.ugcs.geohammer.format.SgyFile;

public enum CoverageStatus {
	NOT_COVERED("Not Covered"),
	PARTIALLY_COVERED("Partially Covered"),
	FULLY_COVERED("Fully Covered");

	private final String displayName;

	CoverageStatus(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}

	public static CoverageStatus compute(SgyFile file, List<SgyFile> positionFiles) {
		if (file == null || positionFiles == null || positionFiles.isEmpty()) {
			return NOT_COVERED;
		}
		TimeRange range = TimeRange.of(file);
		if (range == null) {
			return NOT_COVERED;
		}
		List<TimeRange> positionRanges = new ArrayList<>();
		for (SgyFile positionFile : positionFiles) {
			TimeRange positionRange = TimeRange.of(positionFile);
			if (positionRange != null && positionRange.overlaps(range)) {
				positionRanges.add(positionRange);
			}
		}
		if (positionRanges.isEmpty()) {
			return NOT_COVERED;
		}
		return range.isCoveredBy(positionRanges)
				? FULLY_COVERED
				: PARTIALLY_COVERED;
	}
}
