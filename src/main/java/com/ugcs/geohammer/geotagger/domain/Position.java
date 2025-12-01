package com.ugcs.geohammer.geotagger.domain;

import com.ugcs.geohammer.math.LinearInterpolator;

public class Position {
	private final long timeMs;
	private double latitude;
	private double longitude;
	private double altitude;


	public Position(long timeMs, double latitude, double longitude, double altitude) {
		this.timeMs = timeMs;
		this.latitude = latitude;
		this.longitude = longitude;
		this.altitude = altitude;
	}

	public long getTimeMs() {
		return timeMs;
	}

	public double getLatitude() {
		return latitude;
	}

	public double getLongitude() {
		return longitude;
	}

	public double getAltitude() {
		return altitude;
	}

	public void interpolateCoordinatesFrom(Position left, Position right, long targetTime) {
		long leftTime = left.getTimeMs();
		long rightTime = right.getTimeMs();

		if (leftTime == rightTime) {
			this.latitude = left.getLatitude();
			this.longitude = left.getLongitude();
			this.altitude = left.getAltitude();
			return;
		}

		this.latitude = LinearInterpolator.interpolate(targetTime, left.getLatitude(), right.getLatitude(), leftTime, rightTime);
		this.longitude = LinearInterpolator.interpolate(targetTime, left.getLongitude(), right.getLongitude(), leftTime, rightTime);
		this.altitude = LinearInterpolator.interpolate(targetTime, left.getAltitude(), right.getAltitude(), leftTime, rightTime);
	}
}