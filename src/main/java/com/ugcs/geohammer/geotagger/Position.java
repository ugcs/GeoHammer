package com.ugcs.geohammer.geotagger;

class Position {
	private final Long timeMs;
	private Double latitude;
	private Double longitude;
	private Double altitude;


	Position(Long timeMs, Double latitude, Double longitude, Double altitude) {
		this.timeMs = timeMs;
		this.latitude = latitude;
		this.longitude = longitude;
		this.altitude = altitude;
	}

	public Long getTimeMs() {
		return timeMs;
	}

	public Double getLatitude() {
		return latitude;
	}

	public void setLatitude(Double latitude) {
		this.latitude = latitude;
	}

	public Double getLongitude() {
		return longitude;
	}

	public void setLongitude(Double longitude) {
		this.longitude = longitude;
	}

	public Double getAltitude() {
		return altitude;
	}

	public void setAltitude(Double altitude) {
		this.altitude = altitude;
	}
}
