package com.ugcs.geohammer.geotagger.domain;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.format.gpr.Trace;

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

	public boolean covers(TimeRange other) {
		return !this.from().isAfter(other.from()) && !this.to().isBefore(other.to());
	}

	public static TimeRange of(SgyFile file) {
		if (file instanceof CsvFile csvFile) {
			return of(csvFile);
		} else if (file instanceof TraceFile traceFile) {
			return of(traceFile);
		} else {
			throw new IllegalArgumentException("Unsupported file type: " + file.getClass().getName());
		}
	}

	public static TimeRange of(CsvFile csvFile) {
		List<GeoData> geoData = csvFile.getGeoData();

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

	public static TimeRange of(TraceFile traceFile) {
		List<Trace> traces = traceFile.getTraces();

		Instant start = null;
		Instant end = null;
		for (Trace trace : traces) {
			GeoData geoData = traceFile.getGeoData().get(trace.getIndex());
			if (geoData != null && geoData.getDateTime() != null) {
				start = geoData.getDateTime().toInstant(ZoneOffset.UTC);
				break;
			}
		}
		for (int i = traces.size() - 1; i >= 0; i--) {
			Trace trace = traces.get(i);
			GeoData geoData = traceFile.getGeoData().get(trace.getIndex());
			if (geoData != null && geoData.getDateTime() != null) {
				end = geoData.getDateTime().toInstant(ZoneOffset.UTC);
				break;
			}
		}
		if (start == null || end == null) {
			return null;
		}
		return new TimeRange(start, end);
	}
}
