package com.ugcs.geohammer.geotagger;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.ugcs.geohammer.format.SgyFile;

import javax.annotation.Nullable;

class CoverageStatusResolver {

	@Nullable
	static CoverageStatus resolve(List<SgyFile> positionFiles, SgyFile dataFile) {
		if (dataFile == null || positionFiles == null || positionFiles.isEmpty()) {
			return CoverageStatus.NotCovered;
		}

		Set<SgyFile> coverageFiles = new HashSet<>();

		Instant dataFileStart = getStartTime(dataFile);
		Instant dataFileEnd = getEndTime(dataFile);

		if (dataFileStart == null || dataFileEnd == null) {
			return null;
		}

		for (SgyFile positionFile : positionFiles) {
			Instant positionStartTime = getStartTime(positionFile);
			Instant positionEndTime = getEndTime(positionFile);

			if (positionStartTime == null || positionEndTime == null) {
				continue;
			}

			boolean overlaps = !positionEndTime.isBefore(dataFileStart) && !positionStartTime.isAfter(dataFileEnd);
			if (!overlaps) {
				continue;
			}

			boolean coversCompletely = !positionStartTime.isAfter(dataFileStart) && !positionEndTime.isBefore(dataFileEnd);
			boolean coversEnd = !positionStartTime.isAfter(dataFileEnd) && !positionEndTime.isBefore(dataFileEnd);
			boolean coversStart = !positionStartTime.isAfter(dataFileStart) && !positionEndTime.isBefore(dataFileStart);
			boolean ftuCoversPsf = !dataFileStart.isAfter(positionStartTime) && !dataFileEnd.isBefore(positionEndTime);

			if (coversCompletely || coversEnd || coversStart || ftuCoversPsf) {
				coverageFiles.add(positionFile);
			}
		}

		if (coverageFiles.isEmpty()) {
			return CoverageStatus.NotCovered;
		}

		Instant minTime = coverageFiles.stream()
				.map(CoverageStatusResolver::getStartTime)
				.filter(Objects::nonNull)
				.min(Instant::compareTo)
				.orElse(dataFileStart);
		Instant maxTime = coverageFiles.stream()
				.map(CoverageStatusResolver::getEndTime)
				.filter(Objects::nonNull)
				.max(Instant::compareTo)
				.orElse(dataFileEnd);

		boolean fullyCovered = !minTime.isAfter(dataFileStart) && !maxTime.isBefore(dataFileEnd);

		return fullyCovered ? CoverageStatus.FullyCovered : CoverageStatus.PartiallyCovered;
	}

	@Nullable
	private static Instant getStartTime(SgyFile sgyFile) {
		LocalDateTime dateTime = sgyFile.getGeoData().getFirst().getDateTime();
		return dateTime != null ? dateTime.toInstant(ZoneOffset.UTC) : null;
	}

	@Nullable
	private static Instant getEndTime(SgyFile sgyFile) {
		LocalDateTime dateTime = sgyFile.getGeoData().getLast().getDateTime();
		return dateTime != null ? dateTime.toInstant(ZoneOffset.UTC) : null;
	}
}
