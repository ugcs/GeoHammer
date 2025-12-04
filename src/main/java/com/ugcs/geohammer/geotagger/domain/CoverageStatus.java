package com.ugcs.geohammer.geotagger.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
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

	public static CoverageStatus determine(List<SgyFile> positionFiles, SgyFile dataFile) {
		if (dataFile == null || positionFiles == null || positionFiles.isEmpty()) {
			return NOT_COVERED;
		}

		TimeRange dataFileTimeRange = TimeRange.of(dataFile);
		if (dataFileTimeRange == null) {
			return NOT_COVERED;
		}

		List<TimeRange> overlappingRanges = new ArrayList<>();
		for (SgyFile positionFile : positionFiles) {
			TimeRange range = TimeRange.of(positionFile);
			if (range != null && range.overlaps(dataFileTimeRange)) {
				overlappingRanges.add(range);
			}
		}
		overlappingRanges.sort(Comparator.comparing(TimeRange::from));

		if (overlappingRanges.isEmpty()) {
			return NOT_COVERED;
		}

		return isCovered(dataFileTimeRange, overlappingRanges) ? FULLY_COVERED : PARTIALLY_COVERED;
	}

	private static boolean isCovered(TimeRange dataRange, List<TimeRange> ranges) {
		Instant datafrom = dataRange.from();

		for (TimeRange range : ranges) {
			if (range.from().isAfter(datafrom)) {
				return false;
			}
			if (range.to().isAfter(datafrom)) {
				datafrom = range.to();
			}
		}

		return !datafrom.isBefore(dataRange.to());
	}
}
