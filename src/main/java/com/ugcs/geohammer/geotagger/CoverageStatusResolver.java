package com.ugcs.geohammer.geotagger;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.geotagger.domain.CoverageStatus;
import com.ugcs.geohammer.geotagger.domain.TimeRange;
import org.jspecify.annotations.Nullable;


public class CoverageStatusResolver {

	private CoverageStatusResolver() {
	}

	public static CoverageStatus determineCoverageStatus(List<SgyFile> positionFiles, SgyFile dataFile) {
		if (dataFile == null || positionFiles == null || positionFiles.isEmpty()) {
			return CoverageStatus.NotCovered;
		}

		TimeRange dataFileTimeRange = toTimeRange(dataFile);

		if (dataFileTimeRange == null) {
			throw new IllegalArgumentException("Data file has no valid time range");
		}

		Set<SgyFile> coverageFiles = positionFiles.stream()
				.filter(positionFile -> {
					TimeRange positionFileTimeRange = toTimeRange(positionFile);
					return positionFileTimeRange != null && positionFileTimeRange.overlaps(dataFileTimeRange);
				})
				.collect(Collectors.toSet());

		if (coverageFiles.isEmpty()) {
			return CoverageStatus.NotCovered;
		}

		TimeRange coverageRange = coverageFiles.stream()
				.map(CoverageStatusResolver::toTimeRange)
				.filter(Objects::nonNull)
				.reduce((first, last) -> new TimeRange(first.start(), last.end()))
				.orElse(dataFileTimeRange);

		boolean fullyCovered = coverageRange.covers(dataFileTimeRange);


		return fullyCovered ? CoverageStatus.FullyCovered : CoverageStatus.PartiallyCovered;

	}

	@Nullable
	public static TimeRange toTimeRange(SgyFile sgyFile) {
		Instant startTime = getStartTime(sgyFile);
		Instant endTime = getEndTime(sgyFile);
		if (startTime == null || endTime == null) {
			return null;
		}
		return new TimeRange(startTime, endTime);
	}

	@Nullable
	private static Instant getStartTime(SgyFile sgyFile) {
		if (sgyFile == null || sgyFile.getGeoData() == null) {
			return null;
		}
		return sgyFile.getGeoData().stream()
				.map(GeoData::getDateTime)
				.filter(Objects::nonNull)
				.findFirst()
				.map(dateTime -> dateTime.toInstant(ZoneOffset.UTC))
				.orElse(null);
	}

	@Nullable
	private static Instant getEndTime(SgyFile sgyFile) {
		if (sgyFile == null || sgyFile.getGeoData() == null) {
			return null;
		}
		return sgyFile.getGeoData().stream()
				.map(GeoData::getDateTime)
				.filter(Objects::nonNull)
				.reduce((first, second) -> second)
				.map(dateTime -> dateTime.toInstant(ZoneOffset.UTC))
				.orElse(null);
	}
}
