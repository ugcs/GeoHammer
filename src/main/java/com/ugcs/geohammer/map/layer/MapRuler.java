package com.ugcs.geohammer.map.layer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import com.ugcs.geohammer.model.ActivationPolicy;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.model.MapField;
import com.ugcs.geohammer.model.ToolNode;
import com.ugcs.geohammer.view.ResourceImageHolder;
import com.ugcs.geohammer.model.Model;
import javafx.geometry.Point2D;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class MapRuler implements Layer {

	private static final int RADIUS = 5;
	private static final double ARROW_LENGTH = 16.0;
	private static final double ARROW_HALF_WIDTH = 6.0;
	private static final BasicStroke ARROW_STROKE =
			new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

	private final MapField mapField;
	private List<LatLon> points = new ArrayList<>();
	@Nullable
	private Integer activePointIndex = null;
	@Nullable
	private Runnable repaintCallback;

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
		points = calculateInitialRulerPoints();
	}

	private List<LatLon> calculateInitialRulerPoints() {
		LatLon center = mapField.getSceneCenter();
		if (center == null) {
			return List.of(
					mapField.screenTolatLon(new Point2D(100, 100)),
					mapField.screenTolatLon(new Point2D(200, 200))
			);
		} else {
			Point2D centerScreen = mapField.latLonToScreen(center);
			double offset = 50.0;
			Point2D left = new Point2D(centerScreen.getX() - offset, centerScreen.getY());
			Point2D right = new Point2D(centerScreen.getX() + offset, centerScreen.getY());
			return List.of(mapField.screenTolatLon(left), mapField.screenTolatLon(right));
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

	private void requestRepaint() {
		if (repaintCallback != null) {
			repaintCallback.run();
		}
	}

	@Override
	public List<ToolNode> getToolNodes() {
		toggleButton.setSelected(false);
		toggleButton.setOnAction(e -> handleToggle());
		return List.of(
				new ToolNode(toggleButton, ActivationPolicy.always())
		);
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
		points.get(index).from(mapField.screenTolatLon(point));
		requestRepaint();
		return true;
	}

	@Override
	public void draw(Graphics2D g2, MapField fixedField) {
		if (points.isEmpty()) {
			return;
		}
		List<Point2D> line = getScreenLine(fixedField);

		Point2D start = line.getFirst();
		Point2D end = line.getLast();
		Graphics2D g = (Graphics2D) g2.create();
		try {
			g.setColor(Color.GREEN);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
			g.draw(new Line2D.Double(start.getX(), start.getY(), end.getX(), end.getY()));
			g.setStroke(ARROW_STROKE);
			g.draw(createArrowHead(start, end));
		} finally {
			g.dispose();
		}

		g2.setColor(Color.WHITE);
		for (Point2D p : line) {
			g2.fill(new Rectangle2D.Double(p.getX() - RADIUS, p.getY() - RADIUS, 2 * RADIUS, 2 * RADIUS));
		}

		if (activePointIndex != null) {
			g2.setColor(Color.BLUE);
			Point2D pa = line.get(activePointIndex);
			g2.draw(new Rectangle2D.Double(pa.getX() - RADIUS, pa.getY() - RADIUS, 2 * RADIUS, 2 * RADIUS));
		}
	}

	private Path2D createArrowHead(Point2D from, Point2D to) {
		Path2D.Double arrow = new Path2D.Double();
		double length = from.distance(to);
		if (length == 0) {
			return arrow;
		}
		double dx = (to.getX() - from.getX()) / length;
		double dy = (to.getY() - from.getY()) / length;
		double baseX = to.getX() - dx * ARROW_LENGTH;
		double baseY = to.getY() - dy * ARROW_LENGTH;
		arrow.moveTo(baseX - dy * ARROW_HALF_WIDTH, baseY + dx * ARROW_HALF_WIDTH);
		arrow.lineTo(to.getX(), to.getY());
		arrow.lineTo(baseX + dy * ARROW_HALF_WIDTH, baseY - dx * ARROW_HALF_WIDTH);
		return arrow;
	}

	private List<Point2D> getScreenLine(MapField field) {
		List<Point2D> line = new ArrayList<>(points.size());
		for (LatLon p : points) {
			line.add(field.latLonToScreen(p));
		}
		return line;
	}

	@Nullable
	public Double getDistanceMeters() {
		if (!isVisible()) {
			return null;
		}
		return points.getFirst().getDistance(points.getLast());
	}

	@Nullable
	public Double getHeading() {
		if (!isVisible()) {
			return null;
		}
		return points.getFirst().getBearing(points.getLast());
	}

	public boolean isVisible() {
		return points.size() > 1;
	}
}
