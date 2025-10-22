package com.github.thecoldwine.sigrun.common.ext;

import com.ugcs.gprvisualizer.draw.MapProvider;
import javafx.geometry.Point2D;
import org.jspecify.annotations.Nullable;

public class MapField {

	private static final double R = 6378137;

	private static final double MIN_ZOOM = 2.0;

	private static final double MAX_ZOOM = 30.0;

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
	
	public Point2D latLonToScreen(@Nullable LatLon latlon) {
		if (latlon == null || getSceneCenter() == null) {
			return new Point2D(0, 0);
		}
		
		Point2D psc = GoogleCoordUtils.latLonToPoint(getSceneCenter(), zoom);
		Point2D p2d = GoogleCoordUtils.latLonToPoint(latlon, zoom);

		return new Point2D(
			(p2d.getX() - psc.getX()),
			(p2d.getY() - psc.getY()));
	}

	/**
	 * Calculate the distance between two LatLon points using the Haversine formula.
	 *
	 * @param latLon1 First LatLon point
	 * @param latLon2 Second LatLon point
	 * @return Distance in meters
	 */
	public Double latLonDistance(LatLon latLon1, LatLon latLon2) {
		double lat1 = toRad(latLon1.getLatDgr());
		double lon1 = toRad(latLon1.getLonDgr());
		double lat2 = toRad(latLon2.getLatDgr());
		double lon2 = toRad(latLon2.getLonDgr());

		double deltaLon = lon2 - lon1;
		double deltaLat = lat2 - lat1;

		double a = Math.pow(Math.sin(deltaLat / 2), 2)
				+ Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(deltaLon / 2), 2);

		return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
	}
	
	public LatLon screenTolatLon(Point2D point) {
		if (getSceneCenter() == null) {
			return GoogleCoordUtils.latLonFromPoint(new Point2D(0, 0), zoom);
		}

		Point2D psc = GoogleCoordUtils.latLonToPoint(getSceneCenter(), zoom);
		Point2D p = new Point2D(
			psc.getX() + point.getX(), 
			psc.getY() + point.getY());
		
		return GoogleCoordUtils.latLonFromPoint(p, zoom);
	}
	
	private double getTileSize() {
		return 256;
	}

	private double getInitialResolution() {
		return 2 * Math.PI * R / getTileSize();
	}

	double resolution(double zoom) { 
		return getInitialResolution() / (Math.pow(2, zoom));
	}
	
	private static double toRad(double degree) {
		return degree * Math.PI / 180;
	}
	
	public double getZoom() {
		return zoom;
	}
	
	public void setZoom(double zoom) {
		this.zoom = Math.clamp(zoom, MIN_ZOOM, MAX_ZOOM);
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
	}

	@Nullable
	public MapProvider getMapProvider() {
		return mapProvider;
	}
}
