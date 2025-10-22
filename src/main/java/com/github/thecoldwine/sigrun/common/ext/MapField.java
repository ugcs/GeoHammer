package com.github.thecoldwine.sigrun.common.ext;

import com.ugcs.gprvisualizer.draw.MapProvider;
import javafx.geometry.Point2D;
import org.jspecify.annotations.Nullable;

public class MapField {

	private static final double R = 6378137;

	public static final double MIN_ZOOM = 2.0;

	public static final double MAX_ZOOM = 30.0;

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
