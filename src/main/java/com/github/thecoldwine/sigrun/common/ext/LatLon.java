package com.github.thecoldwine.sigrun.common.ext;

import java.util.Locale;

import com.ugcs.gprvisualizer.math.CoordinatesMath;

public class LatLon {

	private double latDgr;
	private double lonDgr;

	@Override
	public String toString() {

		return dgrToDegreeMinSec(getLatDgr(), true) + "  " + dgrToDegreeMinSec(getLonDgr(), false);

		//return getLatDgr() +  "   " + getLonDgr();
	}

	public LatLon(double latDgr, double lonDgr) {
		this.latDgr = latDgr;
		this.lonDgr = lonDgr;
	}

	public double getLatDgr() {
		return latDgr;
	}

	public double getLonDgr() {
		return lonDgr;
	}

	public void from(LatLon latLon) {
		this.latDgr = latLon.getLatDgr();
		this.lonDgr = latLon.getLonDgr();
	}

	private String dgrToDegreeMinSec(double dgr, boolean lat) {
		String postfix;
		if (lat) {
			postfix = dgr > 0 ? "N" : "S";
		} else {
			postfix = dgr > 0 ? "E" : "W";
		}

		dgr = Math.abs(dgr);

		int justdgr = (int) dgr;
		int justmin = (int) ((dgr - justdgr) * 60);
		double justsec = (dgr - (double) justdgr - (double) justmin / 60.0) * 3600;

		// 40°02'04.0"S 65°11'00.2"E
		return String.format(Locale.ROOT, "%d°%d'%.3f\"%s", justdgr, justmin, justsec, postfix);
		//return justdgr + "°" + justmin + "'" + justsec + "\"" + postfix;

	}

	public double getDistance(LatLon another) {

		return CoordinatesMath.measure(
				getLatDgr(), getLonDgr(),
				another.getLatDgr(), another.getLonDgr());


	}

	/**
	 * Calculates the midpoint between this LatLon and another LatLon.
	 * Uses spherical coordinates to find the midpoint on the surface of the Earth.
	 *
	 * @param another The second LatLon point.
	 * @return A new LatLon representing the midpoint.
	 */
	public LatLon midpoint(LatLon another) {
		double lat1 = Math.toRadians(latDgr);
		double lon1 = Math.toRadians(lonDgr);
		double lat2 = Math.toRadians(another.getLatDgr());
		double lon2 = Math.toRadians(another.getLonDgr());

		double x1 = Math.cos(lat1) * Math.cos(lon1);
		double y1 = Math.cos(lat1) * Math.sin(lon1);
		double z1 = Math.sin(lat1);

		double x2 = Math.cos(lat2) * Math.cos(lon2);
		double y2 = Math.cos(lat2) * Math.sin(lon2);
		double z2 = Math.sin(lat2);

		double x = x1 + x2, y = y1 + y2, z = z1 + z2;
		double norm = Math.sqrt(x * x + y * y + z * z);
		if (norm < 1e-12) {
			return this;
		}

		x /= norm;
		y /= norm;
		z /= norm;

		double midLat = Math.toDegrees(Math.atan2(z, Math.sqrt(x * x + y * y)));
		double midLon = Math.toDegrees(Math.atan2(y, x));
		midLon = ((midLon + 540) % 360) - 180;

		return new LatLon(midLat, midLon);
	}

	public static boolean isValidLatitude(double latitude) {
		if (Double.isNaN(latitude)) {
			return false;
		}
		double abs = Math.abs(latitude);
		return abs > 1e-6 && abs <= 90.0;
	}

	public static boolean isValidLongitude(double longitude) {
		if (Double.isNaN(longitude)) {
			return false;
		}
		double abs = Math.abs(longitude);
		return abs > 1e-6 && abs <= 180.0;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof LatLon another) {
			return getLatDgr() == another.getLatDgr()
					&& getLonDgr() == another.getLonDgr();
		}
		return false;
	}

	@Override
	public int hashCode() {
		return (int) (getLatDgr() * 1000000 + getLonDgr() * 1000);
	}

}
