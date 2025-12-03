package com.ugcs.geohammer.geotagger;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.geotagger.domain.CoverageStatus;
import com.ugcs.geohammer.geotagger.domain.TimeRange;


public class CoverageStatusResolver {

	private CoverageStatusResolver() {
	}

	public static CoverageStatus determineCoverageStatus(List<SgyFile> positionFiles, SgyFile dataFile) {
		if (dataFile == null || positionFiles == null || positionFiles.isEmpty()) {
			return CoverageStatus.NotCovered;
		}

		TimeRange dataFileTimeRange = TimeRange.of(dataFile);

		if (dataFileTimeRange == null) {
			return CoverageStatus.NotCovered;
		}

		Set<SgyFile> coverageFiles = positionFiles.stream()
				.filter(positionFile -> {
					TimeRange positionFileTimeRange = TimeRange.of(positionFile);
					return positionFileTimeRange != null && positionFileTimeRange.overlaps(dataFileTimeRange);
				})
				.collect(Collectors.toSet());

		if (coverageFiles.isEmpty()) {
			return CoverageStatus.NotCovered;
		}

		TimeRange coverageRange = coverageFiles.stream()
				.map(TimeRange::of)
				.filter(Objects::nonNull)
				.reduce((first, last) -> new TimeRange(first.from(), last.to()))
				.orElse(dataFileTimeRange);

		boolean fullyCovered = coverageRange.covers(dataFileTimeRange);


		return fullyCovered ? CoverageStatus.FullyCovered : CoverageStatus.PartiallyCovered;

	}
}
