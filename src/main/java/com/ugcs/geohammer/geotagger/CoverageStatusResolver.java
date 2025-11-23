package com.ugcs.geohammer.geotagger;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.ugcs.geohammer.format.SgyFile;

import javax.annotation.Nullable;

class CoverageStatusResolver {

	private CoverageStatusResolver() {}

	@Nullable
	static CoverageStatus resolve(List<SgyFile> positionFiles, SgyFile dataFile) {
		if (dataFile == null || positionFiles == null || positionFiles.isEmpty()) {
			return CoverageStatus.NotCovered;
		}

		Set<SgyFile> coverageFiles = new HashSet<>();

		Instant dataFileStartTime = dataFile.getStartTime();
		Instant dataFileEndTime = dataFile.getEndTime();

		if (dataFileStartTime == null || dataFileEndTime == null) {
			return null;
		}

		for (SgyFile positionFile : positionFiles) {
			Instant positionStartTime = positionFile.getStartTime();
			Instant positionEndTime = positionFile.getEndTime();

			if (positionStartTime == null || positionEndTime == null) {
				continue;
			}

			boolean overlaps = !positionEndTime.isBefore(dataFileStartTime) && !positionStartTime.isAfter(dataFileEndTime);
			if (!overlaps) {
				continue;
			}

			boolean coversCompletely = !positionStartTime.isAfter(dataFileStartTime) && !positionEndTime.isBefore(dataFileEndTime);
			boolean coversEnd = !positionStartTime.isAfter(dataFileEndTime) && !positionEndTime.isBefore(dataFileEndTime);
			boolean coversStart = !positionStartTime.isAfter(dataFileStartTime) && !positionEndTime.isBefore(dataFileStartTime);
			boolean dataFileContainsPositionFile = !dataFileStartTime.isAfter(positionStartTime) && !dataFileEndTime.isBefore(positionEndTime);

			if (coversCompletely || coversEnd || coversStart || dataFileContainsPositionFile) {
				coverageFiles.add(positionFile);
			}
		}

		if (coverageFiles.isEmpty()) {
			return CoverageStatus.NotCovered;
		}

		Instant minTime = coverageFiles.stream()
				.map(SgyFile::getStartTime)
				.filter(Objects::nonNull)
				.min(Instant::compareTo)
				.orElse(dataFileStartTime);
		Instant maxTime = coverageFiles.stream()
				.map(SgyFile::getEndTime)
				.filter(Objects::nonNull)
				.max(Instant::compareTo)
				.orElse(dataFileEndTime);

		boolean fullyCovered = !minTime.isAfter(dataFileStartTime) && !maxTime.isBefore(dataFileEndTime);

		return fullyCovered ? CoverageStatus.FullyCovered : CoverageStatus.PartiallyCovered;
	}
}
