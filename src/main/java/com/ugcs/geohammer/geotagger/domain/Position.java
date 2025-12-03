package com.ugcs.geohammer.geotagger.domain;

import java.time.ZoneOffset;

public record Position(long time, double latitude, double longitude, double altitude) {
	public static Position of(com.ugcs.geohammer.format.GeoData geoData) {
		return new Position(
				geoData.getDateTime().toInstant(ZoneOffset.UTC).toEpochMilli(),
				geoData.getLatitude(),
				geoData.getLongitude(),
				geoData.getAltitude() != null ? geoData.getAltitude() : 0.0
		);
	}
}