package com.ugcs.geohammer.geotagger.domain;

import com.ugcs.geohammer.format.GeoData;

import java.time.ZoneOffset;

public record Position(long time, double latitude, double longitude, double altitude) {
	public static Position of(GeoData geoData) {
		return new Position(
				geoData.getDateTime().toInstant(ZoneOffset.UTC).toEpochMilli(),
				geoData.getLatitude(),
				geoData.getLongitude(),
				geoData.getAltitude() != null ? geoData.getAltitude() : 0.0
		);
	}
}