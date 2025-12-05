package com.ugcs.geohammer.geotagger.domain;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.SgyFile;

public record TimeRange(Instant from, Instant to) {

	public TimeRange {
		if (from == null || to == null) {
			throw new IllegalArgumentException("Start and to times must not be null");
		}
		if (from.isAfter(to)) {
			throw new IllegalArgumentException("Start time must not be after to time");
		}
	}

	public boolean overlaps(TimeRange other) {
		return !other.to.isBefore(this.from) && !other.from.isAfter(this.to);
	}

	public boolean isCoveredBy(List<TimeRange> ranges) {
		ranges.sort(Comparator.comparing(TimeRange::from));
		Instant from = this.from;
		for (TimeRange range : ranges) {
			if (range.to().isBefore(from)) {
				continue;
			}
			if (range.from().isAfter(from)) {
				return false;
			}
			if (!(range.to().isBefore(to))) {
				return true;
			}
			from = range.to().plusMillis(1);
		}
		return from.isAfter(to);
	}

	public static TimeRange of(SgyFile file) {
		List<GeoData> geoData = file.getGeoData();

		Instant start = null;
		Instant end = null;
		for (GeoData value : geoData) {
			if (value != null && value.getDateTime() != null) {
				start = value.getDateTime().toInstant(ZoneOffset.UTC);
				break;
			}
		}
		for (int i = geoData.size() - 1; i >= 0; i--) {
			GeoData value = geoData.get(i);
			if (value != null && value.getDateTime() != null) {
				end = value.getDateTime().toInstant(ZoneOffset.UTC);
				break;
			}
		}
		if (start == null || end == null) {
			return null;
		}
		return new TimeRange(start, end);
	}
}
