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
	private ArrayList<LatLon> points = new ArrayList<>();
	private ArrayList<Boolean> isMiddle = new ArrayList<>();
	@Nullable
	private Integer activePointIndex = null;
	@Nullable
	private Runnable repaintCallback;
	private DistanceConverterService.Unit distanceUnit = DistanceConverterService.Unit.METERS;

	private final ToggleButton buttonMeasureMode = ResourceImageHolder.setButtonImage(ResourceImageHolder.RULER, new ToggleButton());
	{
		buttonMeasureMode.setTooltip(new Tooltip("Measure distance"));
	}

	public MapRuler(Model model) {
		this.mapField = model.getMapField();
	}

	public void clear() {
		points = new ArrayList<>();
		isMiddle = new ArrayList<>();
		activePointIndex = null;
	}

	public void init() {
		points = new ArrayList<>(calculateInitialRulerPoints());
		isMiddle = new ArrayList<>(Arrays.asList(false, false));
		addMiddlePoint(0);
	}

	private List<LatLon> calculateInitialRulerPoints() {
		LatLon centerLatLon = mapField.getSceneCenter();
		if (centerLatLon == null) {
			// Fallback to a default position if centerLatLon is null
			return Arrays.asList(
					mapField.screenTolatLon(new Point2D(100, 100)),
					mapField.screenTolatLon(new Point2D(200, 200))
			);
		} else {
			Point2D centerScreen = mapField.latLonToScreen(centerLatLon);
			double offset = 50;
			Point2D left = new Point2D(centerScreen.getX() - offset, centerScreen.getY());
			Point2D right = new Point2D(centerScreen.getX() + offset, centerScreen.getY());
			return Arrays.asList(
					mapField.screenTolatLon(left),
					mapField.screenTolatLon(right)
			);
		}
	}

	public void initButtons() {
		buttonMeasureMode.setSelected(false);
	}

	private void updateMeasureMode() {
		if (buttonMeasureMode.isSelected()) {
			init();
		} else {
			clear();
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

	public List<Node> getToolNodes2() {
		initButtons();
		buttonMeasureMode.setOnAction(e -> updateMeasureMode());
		return List.of(buttonMeasureMode);
	}

	@Override
	public boolean mousePressed(Point2D point) {
		if (points.size() == 1) {
			// Add second point on first click
			LatLon latLon = mapField.screenTolatLon(point);
			points.add(latLon);
			isMiddle.add(false);
			requestRepaint();
			return true;
		}
		// Existing logic for selecting points
		List<Point2D> border = getScreenLine(mapField);
		for (int i = 0; i < border.size(); i++) {
			Point2D p = border.get(i);
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
			if (isMiddle.get(activePointIndex)) {
				isMiddle.set(activePointIndex, false);
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
			if (!isMiddle.get(i)) {
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

	private List<Point2D> getScreenLine(MapField fld) {
		return points.stream()
				.map(fld::latLonToScreen)
				.toList();
	}

	public void addMiddlePoint(int index) {
		if (index < 0 || index >= points.size() - 1) return;
		LatLon p1 = points.get(index);
		LatLon p2 = points.get(index + 1);
		Point2D midScreen = mapField.latLonToScreen(p1).midpoint(mapField.latLonToScreen(p2));
		LatLon midLatLon = mapField.screenTolatLon(midScreen);
		if (!points.contains(midLatLon)) {
			points.add(index + 1, midLatLon);
			isMiddle.add(index + 1, true);
		}
	}

	private void addMiddlePointsAround(int index) {
		if (index <= 0 || index >= points.size() - 1) {
			return;
		}
		addMiddlePoint(index - 1);
		addMiddlePoint(index + 1);
	}

	public String getDistanceString() {
		if (points.isEmpty()) {
			return "";
		}
		double totalDistance = 0.0;
		for (int i = 0; i < points.size() - 1; i++) {
			totalDistance += mapField.latLonDistance(points.get(i), points.get(i + 1));
		}
		double value = DistanceConverterService.convert(totalDistance, distanceUnit);
		return String.format("Distance: %.2f", value);
	}

	public boolean isVisible() {
		return !points.isEmpty();
	}
}