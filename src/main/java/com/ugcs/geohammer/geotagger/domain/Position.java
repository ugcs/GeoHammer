package com.ugcs.geohammer.geotagger.domain;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.model.LatLon;
import org.jspecify.annotations.Nullable;

public record Position(long time, double latitude, double longitude, double altitude) {

	public static @Nullable Position of(GeoData value) {
		if (value == null) {
			return null;
		}
		Long time = value.getTimestamp();
		Double latitude = value.getLatitude();
		Double longitude = value.getLongitude();
		Double altitude = value.getAltitude();
		if (time == null || latitude == null || longitude == null) {
			return null;
		}
		return new Position(time, latitude, longitude, altitude != null ? altitude : 0.0);
	}

	public LatLon getLatLon() {
		return new LatLon(latitude, longitude);
	}
}