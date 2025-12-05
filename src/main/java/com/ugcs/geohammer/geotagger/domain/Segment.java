package com.ugcs.geohammer.geotagger.domain;

import com.ugcs.geohammer.math.LinearInterpolator;

public class Segment {

	private final Position from;

	private final Position to;

	public Segment(Position from, Position to) {
		this.from = from;
		this.to = to;
	}

	public Position getFrom() {
		return from;
	}

	public Position getTo() {
		return to;
	}

	public Position interpolatePosition(long time) {
		long fromTime = from.time();
		long toTime = to.time();

		return new Position(
				time,
				LinearInterpolator.interpolate(time, fromTime, toTime, from.latitude(), to.latitude()),
				LinearInterpolator.interpolate(time, fromTime, toTime, from.longitude(), to.longitude()),
				LinearInterpolator.interpolate(time, fromTime, toTime, from.altitude(), to.altitude())
		);
	}
}
