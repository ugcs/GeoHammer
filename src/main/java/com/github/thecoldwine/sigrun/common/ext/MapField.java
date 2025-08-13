package com.github.thecoldwine.sigrun.common.ext;

import com.ugcs.gprvisualizer.draw.MapProvider;
import javafx.geometry.Point2D;
import org.jspecify.annotations.Nullable;

public class MapField {

	private static final double R = 6378137;

	@Nullable
	private LatLon pathCenter;
	@Nullable
	private LatLon pathLt;
	@Nullable
	private LatLon pathRb;
	@Nullable
	private LatLon sceneCenter;

	private int zoom;

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
		
		Point2D psc = GoogleCoordUtils.createInfoWindowContent(getSceneCenter(), getZoom());
		Point2D p2d = GoogleCoordUtils.createInfoWindowContent(latlon, getZoom());
		
		Point2D result = new Point2D(
			(p2d.getX() - psc.getX()), 
			(p2d.getY() - psc.getY()));
		
		return result;		
	}

	public Double latLonDistance(LatLon ll1, LatLon ll2) {
		double lat1 = toRad(ll1.getLatDgr());
		double lon1 = toRad(ll1.getLonDgr());
		double lat2 = toRad(ll2.getLatDgr());
		double lon2 = toRad(ll2.getLonDgr());

		double deltaLon = lon2 - lon1;
		double deltaLat = lat2 - lat1;

		double a = Math.pow(Math.sin(deltaLat / 2), 2)
				+ Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(deltaLon / 2), 2);

		return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
	}
	
	public LatLon screenTolatLon(Point2D point) {
		if (getSceneCenter() == null) {
			return GoogleCoordUtils.llFromP(new Point2D(0, 0), getZoom());
		}

		Point2D psc = GoogleCoordUtils.createInfoWindowContent(getSceneCenter(), getZoom());
		Point2D p = new Point2D(
			psc.getX() + point.getX(), 
			psc.getY() + point.getY());
		
		return GoogleCoordUtils.llFromP(p, getZoom());
	}
	
	//public static final int MAP_SCALE = 1;
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
	
	public int getZoom() {
		return zoom;
	}
	
	public void setZoom(int zoom) {
		this.zoom = Math.max(0, Math.min(30, zoom));
		//this.zoom = Math.max(0, zoom);
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
				&& (Math.abs(scr.getX()) > screenWidth / 2
				|| Math.abs(scr.getY()) > screenHeight / 2)) {
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
