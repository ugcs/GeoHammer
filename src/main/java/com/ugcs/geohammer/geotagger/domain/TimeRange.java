package com.ugcs.geohammer.geotagger.domain;

import java.time.Instant;

public record TimeRange(Instant start, Instant end) {

	public TimeRange {
		if (start == null || end == null) {
			throw new IllegalArgumentException("Start and end times must not be null");
		}
		if (start.isAfter(end)) {
			throw new IllegalArgumentException("Start time must not be after end time");
		}
	}

	public boolean overlaps(TimeRange other) {
		return !other.end.isBefore(this.start) && !other.start.isAfter(this.end);
	}

	public boolean covers(TimeRange other) {
		return !this.start().isAfter(other.start()) && !this.end().isBefore(other.end());
	}
}
