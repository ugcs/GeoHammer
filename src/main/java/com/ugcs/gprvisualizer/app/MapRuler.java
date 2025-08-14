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

import javax.annotation.Nullable;

@Component
public class MapRuler implements Layer {

	private static final int RADIUS = 5;

	private final MapField mapField;
	private List<LatLon> points = new ArrayList<>();
	private List<Boolean> midpointFlags = new ArrayList<>();
	@Nullable
	private Integer activePointIndex = null;
	@Nullable
	private Runnable repaintCallback;
	@Nullable
	private DistanceConverterService.Unit distanceUnit;

	private final ToggleButton toggleButton = ResourceImageHolder.setButtonImage(ResourceImageHolder.RULER, new ToggleButton());
	{
		toggleButton.setTooltip(new Tooltip("Measure distance"));
	}

	public MapRuler(Model model) {
		this.mapField = model.getMapField();
	}

	private void clearPoints() {
		points = new ArrayList<>();
		midpointFlags = new ArrayList<>();
		activePointIndex = null;
	}

	private void initializePoints() {
		points = new ArrayList<>(calculateInitialRulerPoints());
		addMiddlePoint(0);
	}

	private List<LatLon> calculateInitialRulerPoints() {
		LatLon centerMapContainer = mapField.getSceneCenter();
		midpointFlags = new ArrayList<>(Arrays.asList(false, false));
		if (centerMapContainer == null) {
			return Arrays.asList(
					mapField.screenTolatLon(new Point2D(100, 100)),
					mapField.screenTolatLon(new Point2D(200, 200))
			);
		} else {
			Point2D centerScreen = mapField.latLonToScreen(centerMapContainer);
			double offset = 50;
			Point2D left = new Point2D(centerScreen.getX() - offset, centerScreen.getY());
			Point2D right = new Point2D(centerScreen.getX() + offset, centerScreen.getY());
			return Arrays.asList(
					mapField.screenTolatLon(left),
					mapField.screenTolatLon(right)
			);
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
		if (repaintCallback != null) {
			repaintCallback.run();
		}
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
			Point2D p = line.get(i);
			if (point.distance(p) < RADIUS) {
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
		if (points.isEmpty()) {
			return false;
		}
		if (activePointIndex != null) {
			if (midpointFlags.get(activePointIndex)) {
				midpointFlags.set(activePointIndex, false);
				addMiddlePointsAround(activePointIndex);
			}
			requestRepaint();
			activePointIndex = null;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseMove(Point2D point) {
		Integer currentIndex = activePointIndex;
		if (points.isEmpty() || currentIndex == null) {
			return false;
		}
		points.get(currentIndex).from(mapField.screenTolatLon(point));
		requestRepaint();
		return true;
	}

	@Override
	public void draw(Graphics2D g2, MapField fixedField) {
		if (points.isEmpty()) {
			return;
		}
		List<Point2D> line = getScreenLine(mapField);

		g2.setColor(Color.GREEN);
		for (int i = 0; i < line.size() - 1; i++) {
			Point2D p1 = line.get(i);
			Point2D p2 = line.get(i + 1);
			g2.drawLine((int) p1.getX(), (int) p1.getY(), (int) p2.getX(), (int) p2.getY());
		}

		for (int i = 0; i < points.size(); i++) {
			Point2D p = line.get(i);
			if (!midpointFlags.get(i)) {
				g2.setColor(Color.WHITE);
			} else {
				g2.setColor(Color.GRAY);
			}
			g2.fillRect((int) p.getX() - RADIUS, (int) p.getY() - RADIUS, 2 * RADIUS, 2 * RADIUS);
		}

		if (activePointIndex != null) {
			g2.setColor(Color.BLUE);
			Point2D pa = line.get(activePointIndex);
			g2.drawRect((int) pa.getX() - RADIUS, (int) pa.getY() - RADIUS, 2 * RADIUS, 2 * RADIUS);
		}
	}

	private List<Point2D> getScreenLine(MapField mapField) {
		return points.stream()
				.map(mapField::latLonToScreen)
				.toList();
	}

	private void addMiddlePoint(int index) {
		if (index < 0 || index >= points.size() - 1) {
			return;
		}
		LatLon p1 = points.get(index);
		LatLon p2 = points.get(index + 1);
		LatLon midLatLon = p1.midpoint(p2);
		if (!points.contains(midLatLon)) {
			points.add(index + 1, midLatLon);
			midpointFlags.add(index + 1, true);
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
			totalDistanceMeters += mapField.latLonDistance(points.get(i), points.get(i + 1));
		}
		double value;
		if (distanceUnit == null) {
			value = DistanceConverterService.convert(totalDistanceMeters, DistanceConverterService.Unit.getDefault());
		} else {
			value = DistanceConverterService.convert(totalDistanceMeters, distanceUnit);
		}
		return String.format("Distance: %.2f", value);
	}

	public boolean isVisible() {
		return points.size() > 1;
	}
}