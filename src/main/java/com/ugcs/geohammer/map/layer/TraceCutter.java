package com.ugcs.geohammer.map.layer;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.ugcs.geohammer.service.TraceTransform;
import com.ugcs.geohammer.model.TraceKey;
import com.ugcs.geohammer.chart.Chart;
import com.ugcs.geohammer.model.undo.UndoModel;
import com.ugcs.geohammer.model.event.FileOpenedEvent;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
import com.ugcs.geohammer.model.event.UndoStackChanged;
import com.ugcs.geohammer.model.event.WhatChanged;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.model.MapField;
import com.ugcs.geohammer.view.ResourceImageHolder;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.map.RepaintListener;
import com.ugcs.geohammer.model.Model;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;


@Component
public class TraceCutter implements Layer, InitializingBean {
	private static final int RADIUS = 5;

	private final Model model;

	private final UndoModel undoModel;

	private final TraceTransform traceTransform;

	private MapField mapField;

	private PolygonSelector polygonSelector = PolygonSelector.empty();

	private RepaintListener listener;

	private ToggleButton buttonCutMode = ResourceImageHolder.setButtonImage(ResourceImageHolder.SELECT_RECT, new ToggleButton());
	private Button buttonCrop = ResourceImageHolder.setButtonImage(ResourceImageHolder.CROP, new Button());
	private Button buttonSplit = ResourceImageHolder.setButtonImage(ResourceImageHolder.SPLIT, new Button());
	private Button buttonUndo = ResourceImageHolder.setButtonImage(ResourceImageHolder.UNDO, new Button());

	{
		buttonCutMode.setTooltip(new Tooltip("Select area"));
		buttonUndo.setTooltip(new Tooltip("Undo"));
		buttonCrop.setTooltip(new Tooltip("Apply crop"));
		buttonSplit.setTooltip(new Tooltip("Split the line"));
	}

	public TraceCutter(
			Model model,
			UndoModel undoModel,
			TraceTransform traceTransform) {
		this.model = model;
		this.undoModel = undoModel;
		this.traceTransform = traceTransform;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		this.mapField = model.getMapField();
	}

	public void clear() {
		polygonSelector.clear();
		polygonSelector = PolygonSelector.empty();
	}

	public void init() {
		List<LatLon> initialCoordinates = new TraceCutInitializer().initialRect(model);
		polygonSelector = new PolygonSelector(mapField, initialCoordinates);
	}

	public void initButtons() {
		buttonCutMode.setSelected(false);
		buttonCrop.setDisable(true);
		buttonSplit.setDisable(true);
		buttonUndo.setDisable(!undoModel.canUndo());
	}

	private void updateCutMode() {
		if (buttonCutMode.isSelected()) {
			init();
			buttonCrop.setDisable(false);
		} else {
			clear();
			buttonCrop.setDisable(true);
		}
		getListener().repaint();
	}

	private void updateSplit() {
		TraceKey mark = model.getSelectedTraceInCurrentChart();
		buttonSplit.setDisable(mark == null);
	}

	public RepaintListener getListener() {
		return listener;
	}

	public void setListener(RepaintListener listener) {
		this.listener = listener;
	}

	public List<Node> getToolNodes() {
		return Arrays.asList();
	}

	public List<Node> getToolNodes2() {

		initButtons();

		buttonCutMode.setOnAction(e -> updateCutMode());

		buttonCrop.setOnAction(e -> {
			applyCropLines();

			buttonCutMode.setSelected(false);
			updateCutMode();
			model.publishEvent(new WhatChanged(this, WhatChanged.Change.traceCut));
		});

		buttonSplit.setOnAction(e -> applySplitLine());

		buttonUndo.setOnAction(e -> {
			undo();

			buttonCutMode.setSelected(false);
			updateCutMode();
			model.publishEvent(new WhatChanged(this, WhatChanged.Change.traceCut));
		});

		return Arrays.asList(buttonCutMode, buttonCrop, buttonSplit, buttonUndo);
	}

	@Override
	public boolean mousePressed(Point2D point) {
		polygonSelector.select(point);
		getListener().repaint();
		return polygonSelector.hasSelection();
	}

	@Override
	public boolean mouseMove(Point2D point) {
		if (!polygonSelector.hasSelection()) {
			return false;
		}

		polygonSelector.moveSelection(point);
		getListener().repaint();
		return true;
	}

	@Override
	public boolean mouseRightClick(Point2D point) {
		polygonSelector.remove(point);
		getListener().repaint();
		return true;
	}

	@Override
	public void draw(Graphics2D g2, MapField fixedField) {
		if (polygonSelector.numPoints() == 0) {
			return;
		}

		for (int i = 0; i < polygonSelector.numPoints(); i++) {
			Point2D polygonPoint = polygonSelector.get(i);
			Point2D nextPolygonPoint = polygonSelector.get((i + 1) % polygonSelector.numPoints());

			drawLine(g2, polygonPoint, nextPolygonPoint, Color.YELLOW);

			drawPoint(g2, polygonPoint, Color.WHITE);

			Point2D middlePoint = polygonSelector.getMiddle(i);
			drawPoint(g2, middlePoint, Color.GRAY);

			if (polygonSelector.isSelected(i)) {
				drawCircleBorder(g2, polygonPoint, Color.BLUE);
			}
		}
	}

	private void drawLine(Graphics2D graphics, Point2D point1, Point2D point2, Color color) {
		graphics.setColor(color);
		graphics.drawLine((int) point1.getX(), (int) point1.getY(),
				(int) point2.getX(), (int) point2.getY());
	}

	private void drawPoint(Graphics2D graphics, Point2D point, Color color) {
		graphics.setColor(color);
		graphics.fillOval((int) point.getX() - RADIUS,
				(int) point.getY() - RADIUS,
				2 * RADIUS, 2 * RADIUS);
	}

	private void drawCircleBorder(Graphics2D graphics, Point2D point, Color color) {
		graphics.setColor(color);
		graphics.drawOval((int) point.getX() - RADIUS,
				(int) point.getY() - RADIUS,
				2 * RADIUS, 2 * RADIUS);
	}

	private void undo() {
		if (undoModel.canUndo()) {
			model.clearSelectedTraces();
			undoModel.undo();
		}
	}

	private void applyCropLines() {
		model.clearSelectedTraces();

		MapField field = new MapField(mapField);
		field.setZoom(28);

		List<Point2D> cropArea = getScreenPolygon();

		List<SgyFile> files = model.getFileManager().getFiles();
		traceTransform.cropLines(files, field, cropArea);
	}

	private List<Point2D> getScreenPolygon() {
		List<Point2D> polygon = new ArrayList<>();
		for (int i = 0; i < polygonSelector.numPoints(); i++) {
			polygon.add(polygonSelector.get(i));
		}
		return polygon;
	}

	private void applySplitLine() {
		TraceKey mark = model.getSelectedTraceInCurrentChart();
		if (mark == null) {
			return;
		}

		SgyFile file = mark.getFile();
		int splitIndex = mark.getIndex();

		// first index of a new line
		if (traceTransform.isStartOfLine(file, splitIndex)) {
			// nothing to split
			return;
		}

		// clear selection
		Chart chart = model.getChart(file);
		if (chart != null) {
			model.clearSelectedTrace(chart);
		}

		traceTransform.splitLine(file, splitIndex);
	}

	@EventListener
	public void onFileOpened(FileOpenedEvent event) {
		//TODO: maybe we need other event for this
		clear();
		//undoFiles.clear();
		initButtons();
	}

	@EventListener
	private void fileSelected(FileSelectedEvent event) {
		Platform.runLater(this::updateSplit);
	}

	@EventListener
	private void somethingChanged(WhatChanged changed) {
		if (changed.isJustdraw()) {
			Platform.runLater(this::updateSplit);
		}
	}

	@EventListener
	private void undoStackChanged(UndoStackChanged event) {
		Platform.runLater(() -> buttonUndo.setDisable(!undoModel.canUndo()));
	}
}
