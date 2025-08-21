package com.ugcs.gprvisualizer.app;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.github.thecoldwine.sigrun.common.ext.LatLon;
import com.github.thecoldwine.sigrun.common.ext.MapField;
import com.github.thecoldwine.sigrun.common.ext.ResourceImageHolder;
import com.ugcs.gprvisualizer.app.service.DistanceConverterService;
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

	private final MapField mapField;
	private List<Point> points = new ArrayList<>();
	@Nullable
	private Integer activePointIndex = null;
	@Nullable
	private Runnable repaintCallback;
	@Nullable
	private DistanceConverterService.Unit distanceUnit;

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

	public void setDistanceUnit(DistanceConverterService.Unit unit) {
		this.distanceUnit = unit;
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
			// Convert grey to white and insert new grey midpoints around it.
			points.get(index).setMidpoint(false);
			addMiddlePointsAround(index);
		}
		activePointIndex = null;
		requestRepaint();
		return true;
	}

	@Override
	public boolean mouseMove(Point2D point) {
		Integer idx = activePointIndex;
		if (points.isEmpty() || idx == null) {
			return false;
		}

		LatLon newPosition = mapField.screenTolatLon(point);
		points.get(idx).getLocation().from(newPosition);

		// While dragging a white point, keep adjacent grey midpoints centered.
		if (!points.get(idx).isMidpoint()) {
			updateAdjacentMidpoints(idx);
		}

		requestRepaint();
		return true;
	}

	private void updateAdjacentMidpoints(int whiteIdx) {
		// Left grey midpoint between whiteIdx-2 and whiteIdx
		int leftMid = whiteIdx - 1;
		int leftWhite = whiteIdx - 2;
		if (leftMid >= 0 && leftWhite >= 0 && points.get(leftMid).isMidpoint()) {
			LatLon a = points.get(leftWhite).getLocation();
			LatLon b = points.get(whiteIdx).getLocation();
			points.get(leftMid).getLocation().from(a.midpoint(b));
		}

		// Right grey midpoint between whiteIdx and whiteIdx+2
		int rightMid = whiteIdx + 1;
		int rightWhite = whiteIdx + 2;
		if (rightMid < points.size() && rightWhite < points.size() && points.get(rightMid).isMidpoint()) {
			LatLon a = points.get(whiteIdx).getLocation();
			LatLon b = points.get(rightWhite).getLocation();
			points.get(rightMid).getLocation().from(a.midpoint(b));
		}
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
		double value = DistanceConverterService.convert(
				totalDistanceMeters,
				distanceUnit != null ? distanceUnit : DistanceConverterService.Unit.getDefault()
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