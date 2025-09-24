package com.github.thecoldwine.sigrun.common.ext;

import com.ugcs.gprvisualizer.draw.MapProvider;
import javafx.geometry.Point2D;
import org.jspecify.annotations.Nullable;

public class MapField {

	public static final Double MIN_ZOOM = 0.5;
	public static final Double MAX_ZOOM = 30.0;

	private static final double R = 6378137;

	@Nullable
	private LatLon pathCenter;
	@Nullable
	private LatLon pathLt;
	@Nullable
	private LatLon pathRb;
	@Nullable
	private LatLon sceneCenter;

	private double zoom;

	@Nullable
	private MapProvider mapProvider;

	public MapField() {
		this.zoom = 1.0;
	}
	
	public MapField(MapField field) {
		this.pathCenter = field.pathCenter;
		this.sceneCenter = field.sceneCenter;
		this.zoom = field.zoom;
		
		this.pathLt = field.pathLt;
		this.pathRb = field.pathRb;
		
		this.mapProvider = field.mapProvider;
	}

	public boolean isActive() {
		return pathCenter != null;
	}

	private double getTileSize() {
		return 256.0;
	}

	private double mapSize(double zoom) {
		return getTileSize() * Math.pow(2.0, zoom);
	}

	private Point2D toWorld(LatLon latLon, double zoom) {
		double size = mapSize(zoom);
		double x = (latLon.getLonDgr() + 180.0) / 360.0 * size;

		double sinLat = Math.sin(Math.toRadians(latLon.getLatDgr()));
		// Clamp to avoid infinity near the poles
		sinLat = Math.max(-0.9999, Math.min(0.9999, sinLat));

		double y = (0.5 - Math.log((1.0 + sinLat) / (1.0 - sinLat)) / (4.0 * Math.PI)) * size;
		return new Point2D(x, y);
	}

	private LatLon fromWorld(Point2D p, double zoom) {
		double size = mapSize(zoom);

		double lon = (p.getX() / size) * 360.0 - 180.0;

		double y = 0.5 - (p.getY() / size);
		double latRad = Math.PI / 2.0 - 2.0 * Math.atan(Math.exp(-y * 2.0 * Math.PI));
		double lat = Math.toDegrees(latRad);

		return new LatLon(lat, lon);
	}

	public Point2D latLonToScreen(@Nullable LatLon latlon) {
		if (latlon == null || getSceneCenter() == null) {
			return new Point2D(0, 0);
		}

		Point2D centerPoint = toWorld(getSceneCenter(), getZoom());
		Point2D currentPoint = toWorld(latlon, getZoom());

		return new Point2D(currentPoint.getX() - centerPoint.getX(), currentPoint.getY() - centerPoint.getY());
	}

	public LatLon screenTolatLon(Point2D point) {
		if (getSceneCenter() == null) {
			return fromWorld(new Point2D(0, 0), getZoom());
		}

		Point2D pCenter = toWorld(getSceneCenter(), getZoom());
		Point2D p = new Point2D(
				pCenter.getX() + point.getX(),
				pCenter.getY() + point.getY()
		);

		return fromWorld(p, getZoom());
	}

	/**
	 * Calculate the distance between two LatLon points using the Haversine formula.
	 *
	 * @param latLon1 First LatLon point
	 * @param latLon2 Second LatLon point
	 * @return Distance in meters
	 */
	public Double latLonDistance(LatLon latLon1, LatLon latLon2) {
		double lat1 = Math.toRadians(latLon1.getLatDgr());
		double lon1 = Math.toRadians(latLon1.getLonDgr());
		double lat2 = Math.toRadians(latLon2.getLatDgr());
		double lon2 = Math.toRadians(latLon2.getLonDgr());

		double deltaLon = lon2 - lon1;
		double deltaLat = lat2 - lat1;

		double a = Math.pow(Math.sin(deltaLat / 2), 2)
				+ Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(deltaLon / 2), 2);

		return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
	}

	public double getZoom() {
		return zoom;
	}

	public int getZoomInt() {
		// Keep for tile providers or other int-zoom consumers
		return (int) Math.round(zoom);
	}
	
	public void setZoom(double zoom) {
		zoom = Math.clamp(zoom, MIN_ZOOM, MAX_ZOOM);
		this.zoom = zoom;
	}

	@Nullable
	public LatLon getSceneCenter() {
		return sceneCenter;
	}

	public void setSceneCenter(LatLon sceneCenter) {
		if (isActive()) {
			this.sceneCenter = sceneCenter;
		} else {
			this.sceneCenter = null;
		}
	}	

	@Nullable
	public LatLon getPathCenter() {
		return pathCenter;
	}

	public void setPathCenter(LatLon pathCenter) {
		this.pathCenter = pathCenter;
	}	

	public void setPathEdgeLL(LatLon lt, LatLon rb) {
		this.pathLt = lt;
		this.pathRb = rb;
	}

	@Nullable
	public LatLon getPathLeftTop() {
		return pathLt;
	}

	@Nullable
	public LatLon getPathRightBottom() {
		return pathRb;
	}
	
	public void adjustZoom(int screenWidth, int screenHeight) {
		int zoom = 28;
		setZoom(zoom);
		Point2D scr = latLonToScreen(pathRb);
		
		while (zoom > 2
				&& (Math.abs(scr.getX()) > screenWidth / 2.0
				|| Math.abs(scr.getY()) > screenHeight / 2.0)) {
			zoom--;
			setZoom(zoom);
			
			scr = latLonToScreen(pathRb);
		}
	}

	public void setMapProvider(@Nullable MapProvider mapProvider) {
		this.mapProvider = mapProvider;
		setZoom(getZoom());
	}

	@Nullable
	public MapProvider getMapProvider() {
		return mapProvider;
	}
}
