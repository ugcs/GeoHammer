package com.ugcs.geohammer.map.layer;

import java.util.ArrayList;
import java.util.List;

import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.model.MapField;
import javafx.geometry.Point2D;

class PolygonSelector {
	private static final double SELECTION_RADIUS = 5.0;

	private final MapField mapField;

	private final List<LatLon> points;

	private Integer selectedIndex = null;

	public PolygonSelector(MapField mapField, List<LatLon> points) {
		this.mapField = mapField;
		this.points = points;
	}

	Point2D get(int index) {
		return mapField.latLonToScreen(points.get(index));
	}

	Point2D getMiddle(int index) {
		LatLon latLon;
		if (index == points.size() - 1) {
			latLon = points.getLast().midpoint(points.getFirst());
		} else {
			latLon = points.get(index).midpoint(points.get(index + 1));
		}
		return mapField.latLonToScreen(latLon);
	}

	boolean isSelected(int index) {
		return selectedIndex != null && selectedIndex == index;
	}

	int numPoints() {
		return points.size();
	}

	void select(Point2D clickPosition) {
		int closestIndex = -1;
		double minDistance = SELECTION_RADIUS;
		boolean isMiddlePoint = false;

		for (int i = 0; i < points.size(); i++) {
			// Check if clicked on anchor point
			Point2D point = mapField.latLonToScreen(points.get(i));
			double anchorDistance = clickPosition.distance(point);

			if (anchorDistance < minDistance) {
				minDistance = anchorDistance;
				closestIndex = i;
				isMiddlePoint = false;
			}

			// Check if clicked on midpoint
			LatLon nextLatLon = points.get((i + 1) % points.size());
			Point2D nextPoint = mapField.latLonToScreen(nextLatLon);
			Point2D middlePoint = point.midpoint(nextPoint);
			double middleDistance = clickPosition.distance(middlePoint);

			if (middleDistance < minDistance) {
				minDistance = middleDistance;
				closestIndex = i;
				isMiddlePoint = true;
			}
		}

		if (closestIndex != -1) {
			if (isMiddlePoint) {
				addMiddle(closestIndex);
				selectedIndex = closestIndex + 1;
			} else {
				selectedIndex = closestIndex;
			}
		}
	}

	private void addMiddle(int index) {
		LatLon middle;
		if (index == points.size() - 1) {
			middle = points.getLast().midpoint(points.getFirst());
		} else {
			middle = points.get(index).midpoint(points.get(index + 1));
		}
		points.add(index + 1, middle);
	}

	void moveSelection(Point2D to) {
		if (selectedIndex != null) {
			LatLon latLon = mapField.screenTolatLon(to);
			points.set(selectedIndex, latLon);
		}
	}

	void remove(Point2D clickPosition) {
		if (points.size() <= 4) {
			return;
		}

		int closestIndex = -1;
		double minDistance = SELECTION_RADIUS;

		for (int i = 0; i < points.size(); i++) {
			Point2D point = mapField.latLonToScreen(points.get(i));
			double distance = clickPosition.distance(point);

			if (distance < minDistance) {
				minDistance = distance;
				closestIndex = i;
			}
		}

		if (closestIndex != -1) {
			points.remove(closestIndex);
		}
	}

	void clear() {
		points.clear();
	}

	public static PolygonSelector empty() {
		return new EmptyPolygonSelector();
	}

	private static class EmptyPolygonSelector extends PolygonSelector {
		EmptyPolygonSelector() {
			super(null, new ArrayList<>());
		}

		@Override
		public Point2D get(int index) {
			return new Point2D(0, 0);
		}

		@Override
		public Point2D getMiddle(int index) {
			return new Point2D(0, 0);
		}

		@Override
		public boolean isSelected(int index) {
			return false;
		}

		@Override
		public int numPoints() { return 0; }

		@Override
		public void select(Point2D point) { /* do nothing */ }

		@Override
		public void moveSelection(Point2D point) { /* do nothing */ }

		@Override
		public void remove(Point2D point) { /* do nothing */ }

		@Override
		public void clear() { /* do nothing */ }
	}
}
