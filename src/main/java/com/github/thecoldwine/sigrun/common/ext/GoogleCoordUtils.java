package com.github.thecoldwine.sigrun.common.ext;


import javafx.geometry.Point2D;

public class GoogleCoordUtils {

	public static final double EARTH_R = 6378137.0;

	public static final int TILE_SIZE = 256;

	private GoogleCoordUtils() {
	}
	
	public static LatLon latLonFromPoint(Point2D p, double zoom) {
		double scale = Math.pow(2, zoom);
		
		Point2D pixelCoordinate = new Point2D(
				p.getX() / scale,
				p.getY() / scale);
		
		return unproject(pixelCoordinate);
		
	}

	public static Point2D latLonToPoint(LatLon latLng, double zoom) {
		double scale = Math.pow(2, zoom);

		Point2D worldCoordinate = project(latLng);

		Point2D pixelCoordinate = new Point2D(
			worldCoordinate.getX() * scale,
			worldCoordinate.getY() * scale);

		return pixelCoordinate;
	}

	// The mapping between latitude, longitude and pixels is defined by the web
	// mercator projection.
	public static Point2D project(LatLon latLng) {
		double siny = Math.sin(latLng.getLatDgr() * Math.PI / 180);

		// Truncating to 0.9999 effectively limits latitude to 89.189. This is
		// about a third of a tile past the edge of the world tile.
		siny = Math.clamp(siny, -0.9999, 0.9999);

		return new Point2D(
				TILE_SIZE * (0.5 + latLng.getLonDgr() / 360),
				TILE_SIZE * (0.5 - Math.log((1 + siny) / (1 - siny)) / (4 * Math.PI)));
	}
	
	public static LatLon unproject(Point2D wp) {
		
		//double siny = Math.sin(latLng.getLatDgr() * Math.PI / 180);

		// Truncating to 0.9999 effectively limits latitude to 89.189. This is
		// about a third of a tile past the edge of the world tile.
		//siny = Math.min(Math.max(siny, -0.9999), 0.9999);

		//new Point2D.Double(
		//	TILE_SIZE * (0.5 + latLng.getLonDgr() / 360),
		
		double lonDgr = (wp.getX() / TILE_SIZE - 0.5) * 360;
		
		//x = TILE_SIZE * (0.5 - Math.log((1 + siny) / (1 - siny)) / (4 * Math.PI))
		
		// (0.5 - x / TILE_SIZE) * (4 * Math.PI)   =   Math.log((1 + siny) / (1 - siny)) 
		
		double ww = (0.5 - wp.getY() / TILE_SIZE ) * (4 * Math.PI);
		
		//ww = Math.log((1 + siny) / (1 - siny));
		double zz = Math.pow(Math.E, ww);
		//(1 + siny) / (1 - siny) = zz;
		double sssn = (zz-1)/(zz+1);
		double latDgr =  Math.asin(sssn)*180/Math.PI;
		
		
		return new LatLon(latDgr, lonDgr);
	}

	public static double getPixelSize(LatLon latlon, double zoom) {
		double scale = Math.pow(2, zoom);

		double earthCircumference = 2 * Math.PI * EARTH_R;
		return Math.cos(Math.toRadians(latlon.getLatDgr())) * earthCircumference
				/ ((long)TILE_SIZE * scale);
	}
}
