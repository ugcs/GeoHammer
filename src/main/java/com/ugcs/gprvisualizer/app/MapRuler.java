package com.ugcs.gprvisualizer.app;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.github.thecoldwine.sigrun.common.ext.LatLon;
import com.github.thecoldwine.sigrun.common.ext.MapField;
import com.github.thecoldwine.sigrun.common.ext.ResourceImageHolder;
import com.ugcs.gprvisualizer.draw.Layer;
import com.ugcs.gprvisualizer.gpr.Model;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Component
public class MapRuler implements Layer {

	private static final int RADIUS = 5;
	private static final double STRAIGHTNESS_TOLERANCE_PX = 2.0;
	private static final double MIDPOINT_TOLERANCE_PX = 2.0;

	private final MapField mapField;
	private List<Point> points = new ArrayList<>();
	@Nullable
	private Integer activePointIndex = null;
	@Nullable
	private Runnable repaintCallback;
	@Nullable
	private TraceUnit distanceTraceUnit;

	private final ToggleButton toggleButton =
			ResourceImageHolder.setButtonImage(ResourceImageHolder.RULER, new ToggleButton());
	{
		toggleButton.setTooltip(new Tooltip("Measure distance"));
	}

	public MapRuler(Model model) {
		this.mapField = model.getMapField();
	}

	private void clearPoints() {
		points = new ArrayList<>();
		activePointIndex = null;
	}

	private void initializePoints() {
		List<LatLon> initialPositions = calculateInitialRulerPoints();
		this.points = new ArrayList<>(initialPositions.size() + 1);
		for (LatLon pos : initialPositions) {
			this.points.add(new Point(pos, false));
		}
		addMiddlePoint(0);
	}

	private List<LatLon> calculateInitialRulerPoints() {
		LatLon center = mapField.getSceneCenter();
		if (center == null) {
			return Arrays.asList(
					mapField.screenTolatLon(new Point2D(100, 100)),
					mapField.screenTolatLon(new Point2D(200, 200))
			);
		} else {
			Point2D centerScreen = mapField.latLonToScreen(center);
			double offset = 50.0;
			Point2D left = new Point2D(centerScreen.getX() - offset, centerScreen.getY());
			Point2D right = new Point2D(centerScreen.getX() + offset, centerScreen.getY());
			return Arrays.asList(mapField.screenTolatLon(left), mapField.screenTolatLon(right));
		}
	}

	private void handleToggle() {
		if (toggleButton.isSelected()) {
			initializePoints();
		} else {
			clearPoints();
		}
		requestRepaint();
	}

	public void setRepaintCallback(Runnable repaintCallback) {
		this.repaintCallback = repaintCallback;
	}

	public void setDistanceUnit(TraceUnit traceUnit) {
		this.distanceTraceUnit = traceUnit;
		requestRepaint();
	}

	private void requestRepaint() {
		if (repaintCallback != null) {
			repaintCallback.run();
		}
	}

	public List<Node> buildToolNodes() {
		toggleButton.setSelected(false);
		toggleButton.setOnAction(e -> handleToggle());
		return List.of(toggleButton);
	}

	@Override
	public boolean mousePressed(Point2D point) {
		List<Point2D> line = getScreenLine(mapField);
		for (int i = 0; i < line.size(); i++) {
			if (point.distance(line.get(i)) < RADIUS) {
				activePointIndex = i;
				requestRepaint();
				return true;
			}
		}
		activePointIndex = null;
		return false;
	}

	@Override
	public boolean mouseRelease(Point2D point) {
		if (points.isEmpty() || activePointIndex == null) {
			return false;
		}
		int index = activePointIndex;
		if (points.get(index).isMidpoint()) {
			points.get(index).setMidpoint(false);
			addMiddlePointsAround(index);
		} else {
			int leftMid = index - 1;
			int leftAnchorIndex = index - 2;
			int rightMid = index + 1;
			int rightAnchorIndex = index + 2;
			if (canCollapseSegment(index)) {
				LatLon center = points.get(leftAnchorIndex).getLocation()
						.midpoint(points.get(rightAnchorIndex).getLocation());
				points.get(index).getLocation().from(center);
				points.get(index).setMidpoint(true);

				points.remove(rightMid);
				points.remove(leftMid);
			}
		}
		activePointIndex = null;
		requestRepaint();
		return true;
	}

	@Override
	public boolean mouseMove(Point2D point) {
		Integer index = activePointIndex;
		if (points.isEmpty() || index == null) {
			return false;
		}

		LatLon newPosition = mapField.screenTolatLon(point);
		points.get(index).getLocation().from(newPosition);

		if (!points.get(index).isMidpoint()) {
			updateAdjacentMidpoints(index);
		}

		requestRepaint();
		return true;
	}

	private void updateAdjacentMidpoints(int anchorIndex) {
		int leftMid = anchorIndex - 1;
		int leftAnchorIndex = anchorIndex - 2;
		if (leftMid >= 0 && leftAnchorIndex >= 0 && points.get(leftMid).isMidpoint()) {
			LatLon leftAnchorPosition = points.get(leftAnchorIndex).getLocation();
			LatLon rightAnchorPosition = points.get(anchorIndex).getLocation();
			points.get(leftMid).getLocation().from(leftAnchorPosition.midpoint(rightAnchorPosition));
		}

		int rightMid = anchorIndex + 1;
		int rightAnchorIndex = anchorIndex + 2;
		if (rightMid < points.size() && rightAnchorIndex < points.size() && points.get(rightMid).isMidpoint()) {
			LatLon leftAnchorPosition = points.get(anchorIndex).getLocation();
			LatLon rightAnchorPosition = points.get(rightAnchorIndex).getLocation();
			points.get(rightMid).getLocation().from(leftAnchorPosition.midpoint(rightAnchorPosition));
		}
	}

	private boolean canCollapseSegment(int anchorIndex) {
		int leftMid = anchorIndex - 1;
		int leftAnchorIndex = anchorIndex - 2;
		int rightMid = anchorIndex + 1;
		int rightAnchorIndex = anchorIndex + 2;

		if (leftAnchorIndex < 0 || rightAnchorIndex >= points.size()) {
			return false;
		}
		if (leftMid < 0 || rightMid >= points.size()) {
			return false;
		}
		if (points.get(anchorIndex).isMidpoint()) {
			return false;
		}
		if (!points.get(leftMid).isMidpoint() || !points.get(rightMid).isMidpoint()) {
			return false;
		}

		Point2D leftAnchorPoint = mapField.latLonToScreen(points.get(leftAnchorIndex).getLocation());
		Point2D leftMidPoint = mapField.latLonToScreen(points.get(leftMid).getLocation());
		Point2D anchorPoint  = mapField.latLonToScreen(points.get(anchorIndex).getLocation());
		Point2D rightMidPoint = mapField.latLonToScreen(points.get(rightMid).getLocation());
		Point2D rightAnchorPoint = mapField.latLonToScreen(points.get(rightAnchorIndex).getLocation());

		if (!areCollinear(leftAnchorPoint, anchorPoint, rightAnchorPoint)) {
			return false;
		}

		Point2D midLeftAnchorPoint = screenMidpoint(leftAnchorPoint, anchorPoint);
		Point2D midAnchorRightPoint = screenMidpoint(anchorPoint, rightAnchorPoint);
		return approxEquals(leftMidPoint, midLeftAnchorPoint) && approxEquals(rightMidPoint, midAnchorRightPoint);
	}

	private boolean areCollinear(Point2D p, Point2D q, Point2D r) {
		double vx1 = q.getX() - p.getX();
		double vy1 = q.getY() - p.getY();
		double vx2 = r.getX() - q.getX();
		double vy2 = r.getY() - q.getY();
		double cross = Math.abs(vx1 * vy2 - vy1 * vx2);
		double scale = Math.hypot(vx1, vy1) + Math.hypot(vx2, vy2);
		if (scale == 0) {
			return true;
		}
		return cross / scale < STRAIGHTNESS_TOLERANCE_PX;
	}

	private Point2D screenMidpoint(Point2D point1, Point2D point2) {
		return new Point2D((point1.getX() + point2.getX()) / 2.0, (point1.getY() + point2.getY()) / 2.0);
	}

	private boolean approxEquals(Point2D point1, Point2D point2) {
		return point1.distance(point2) <= MIDPOINT_TOLERANCE_PX;
	}

	@Override
	public void draw(Graphics2D g2, MapField fixedField) {
		if (points.isEmpty()) {
			return;
		}
		List<Point2D> line = getScreenLine(fixedField);

		g2.setColor(Color.GREEN);
		for (int i = 0; i < line.size() - 1; i++) {
			Point2D p1 = line.get(i);
			Point2D p2 = line.get(i + 1);
			g2.drawLine((int) p1.getX(), (int) p1.getY(), (int) p2.getX(), (int) p2.getY());
		}

		for (int i = 0; i < points.size(); i++) {
			Point2D p = line.get(i);
			g2.setColor(points.get(i).isMidpoint() ? Color.GRAY : Color.WHITE);
			g2.fillRect((int) p.getX() - RADIUS, (int) p.getY() - RADIUS, 2 * RADIUS, 2 * RADIUS);
		}

		if (activePointIndex != null) {
			g2.setColor(Color.BLUE);
			Point2D pa = line.get(activePointIndex);
			g2.drawRect((int) pa.getX() - RADIUS, (int) pa.getY() - RADIUS, 2 * RADIUS, 2 * RADIUS);
		}
	}

	private List<Point2D> getScreenLine(MapField field) {
		return points.stream()
				.map(p -> field.latLonToScreen(p.getLocation()))
				.toList();
	}

	private void addMiddlePoint(int index) {
		if (index < 0 || index >= points.size() - 1) {
			return;
		}
		LatLon p1 = points.get(index).getLocation();
		LatLon p2 = points.get(index + 1).getLocation();
		LatLon midLatLon = p1.midpoint(p2);
		List<LatLon> pts = points.stream().map(Point::getLocation).toList();
		if (!pts.contains(midLatLon)) {
			points.add(index + 1, new Point(midLatLon, true));
		}
	}

	private void addMiddlePointsAround(int index) {
		if (index <= 0 || index >= points.size() - 1) {
			return;
		}
		addMiddlePoint(index - 1);
		addMiddlePoint(index + 1);
	}

	public String getFormattedDistance() {
		if (points.isEmpty()) {
			return "n/a";
		}
		double totalDistanceMeters = 0.0;
		for (int i = 0; i < points.size() - 1; i++) {
			totalDistanceMeters += mapField.latLonDistance(
					points.get(i).getLocation(), points.get(i + 1).getLocation());
		}
		double value = TraceUnit.convert(
				totalDistanceMeters,
				distanceTraceUnit != null ? distanceTraceUnit : TraceUnit.getDefault()
		);
		return String.format("Distance: %.2f", value);
	}

	public boolean isVisible() {
		return points.size() > 1;
	}

	private static final class Point {
		private final LatLon location;
		private boolean midpoint;

		Point(@Nonnull LatLon location, boolean midpoint) {
			this.location = location;
			this.midpoint = midpoint;
		}

		public LatLon getLocation() { return location; }
		public boolean isMidpoint() { return midpoint; }
		public void setMidpoint(boolean midpoint) { this.midpoint = midpoint; }
	}
}