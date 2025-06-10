package com.ugcs.gprvisualizer.app;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.thecoldwine.sigrun.common.ext.TraceKey;
import com.ugcs.gprvisualizer.event.FileOpenedEvent;
import com.ugcs.gprvisualizer.event.FileSelectedEvent;
import com.ugcs.gprvisualizer.event.WhatChanged;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.context.ApplicationEventPublisher;

import com.github.thecoldwine.sigrun.common.ext.LatLon;
import com.github.thecoldwine.sigrun.common.ext.MapField;
import com.github.thecoldwine.sigrun.common.ext.ResourceImageHolder;
import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.github.thecoldwine.sigrun.common.ext.TraceCutInitializer;
import com.ugcs.gprvisualizer.app.auxcontrol.BaseObject;
import com.ugcs.gprvisualizer.draw.Layer;
import com.ugcs.gprvisualizer.draw.RepaintListener;
import com.ugcs.gprvisualizer.gpr.Model;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;

@Component
public class TraceCutter implements Layer, InitializingBean {

	private static final int RADIUS = 5;
	
	private MapField mapField;
	private List<LatLon> points;	
	private Integer active = null;

	Map<Integer, Boolean> activePoints = new HashMap<>();
	
	private final Model model;
	private final ApplicationEventPublisher eventPublisher;
	private final TraceTransform traceTransform;
	
	private RepaintListener listener;
	
	private ToggleButton buttonCutMode = ResourceImageHolder.setButtonImage(ResourceImageHolder.SELECT_RECT, new ToggleButton());
	private Button buttonCrop = ResourceImageHolder.setButtonImage(ResourceImageHolder.CROP, new Button());
	private Button buttonSplit = ResourceImageHolder.setButtonImage(ResourceImageHolder.SPLIT, new Button());
	private Button buttonUndo = ResourceImageHolder.setButtonImage(ResourceImageHolder.UNDO, new Button());

	private final List<SgyFile> undoFiles = new ArrayList<>();
	
	{
		buttonCutMode.setTooltip(new Tooltip("Select area"));
		buttonUndo.setTooltip(new Tooltip("Undo"));
		buttonCrop.setTooltip(new Tooltip("Apply crop"));
		buttonSplit.setTooltip(new Tooltip("Split the line"));
	}

	public TraceCutter(Model model, ApplicationEventPublisher eventPublisher,
					   TraceTransform traceTransform) {
		this.model = model;
		this.eventPublisher = eventPublisher;
		this.traceTransform = traceTransform;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		this.mapField = model.getMapField();
	}

	public void clear() {
		points = null;
		active = null;
		activePoints.clear();
	}

	public void init() {		
		points = new TraceCutInitializer().initialRect(model);
		activePoints.clear();
	}

	public void initButtons() {
		buttonCutMode.setSelected(false);
		buttonCrop.setDisable(true);
		buttonSplit.setDisable(true);
		buttonUndo.setDisable(undoFiles.isEmpty());
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
			buttonUndo.setDisable(false);
			eventPublisher.publishEvent(new WhatChanged(this, WhatChanged.Change.traceCut));
		});

		buttonSplit.setOnAction(e -> {
			applySplitLine();
		});

		buttonUndo.setOnAction(e -> {
			undo();

			buttonCutMode.setSelected(false);
			updateCutMode();
			buttonUndo.setDisable(true);
			eventPublisher.publishEvent(new WhatChanged(this, WhatChanged.Change.traceCut));
		});

		return Arrays.asList(buttonCutMode, buttonCrop, buttonSplit, buttonUndo);
	}

	@Override
	public boolean mousePressed(Point2D point) {
		if (points == null) {
			return false;
		}
		
		List<Point2D> border = getScreenPoligon(mapField);
		for (int i = 0; i < border.size(); i++) {
			Point2D p = border.get(i);
			if (point.distance(p) < RADIUS) {
				active = i;
				getListener().repaint();
				return true;
			}			
		}
		active = null;
		return false;
	}
	
	@Override
	public boolean mouseRelease(Point2D point) {
		if (points == null) {
			return false;
		}
		
		if (active != null) {
			getListener().repaint();
			active = null;
		
			return true;
		}
		return false;
	}
	
	@Override
	public boolean mouseMove(Point2D point) {
		if (points == null) {
			return false;
		}
		
		if (active == null) {
			return false;
		}
		
		points.get(active).from(mapField.screenTolatLon(point));
		if (active % 2 == 0) {
			if (!isActive(active + 1)) {
				points.get(active + 1).from(TraceCutInitializer.getMiddleOf((active + 2) < points.size() ? points.get(active + 2) : points.get(0), mapField.screenTolatLon(point)));
			}
			if (!isActive(active == 0 ? points.size() - 1 : active - 1)) {
				(active == 0 ? points.get(points.size() - 1) : points.get(active - 1))
				.from(TraceCutInitializer.getMiddleOf(active == 0 ? points.get(points.size() - 2) : points.get(active - 2), mapField.screenTolatLon(point)));
			}
		} else {
			activePoints.put(active, !isInTheMiddle(active));
		}

		getListener().repaint();		
		return true;
	}
	
	private boolean isActive(int pointIndex) {
		return activePoints.computeIfAbsent(pointIndex, i -> false);
	}

	@Override
	public void draw(Graphics2D g2, MapField fixedField) {
		if (points == null) {
			return;
		}
		
		List<Point2D> border = getScreenPoligon(mapField);
		
		for (int i = 0; i < border.size(); i++) {
			
			Point2D p1 = border.get(i);
			Point2D p2 = border.get((i + 1) % border.size());
			
			g2.setColor(Color.YELLOW);
			g2.drawLine((int) p1.getX(), (int) p1.getY(), 
					(int) p2.getX(), (int) p2.getY());
		}
		
		for (int i = 0; i < border.size(); i++) {
			Point2D p1 = border.get(i);			
			
			if ((i+1) % 2 == 0 && !isActive(i)) {
				g2.setColor(Color.GRAY);
			} else {
				g2.setColor(Color.WHITE);
			}
			 
			g2.fillOval((int) p1.getX() - RADIUS, 
					(int) p1.getY() - RADIUS,
					2 * RADIUS, 2 * RADIUS);
			if (active != null && active == i) {
				g2.setColor(Color.BLUE);
				g2.drawOval((int) p1.getX() - RADIUS,
						(int) p1.getY() - RADIUS,
						2 * RADIUS, 2 * RADIUS);
			}			
		}		
	}

	private boolean isInTheMiddle(int pointIndex) {
		List<Point2D> border = getScreenPoligon(mapField);
		return isInTheMiddle(
			border.get(pointIndex - 1), 
			border.get(pointIndex), 
			(pointIndex + 1 < border.size()) ? border.get(pointIndex + 1) : border.get(0));
	}
	
	private boolean isInTheMiddle(Point2D before, Point2D current, Point2D after) {
		double dist = before.distance(after);
		double dist1 = before.distance(current);
		double dist2 = current.distance(after);
		return Math.abs(dist1 + dist2 - dist) < 0.05;
	}

	private List<Point2D> getScreenPoligon(MapField fld) {

		List<Point2D> border = new ArrayList<>();
		for (LatLon ll : points) {
			border.add(fld.latLonToScreen(ll));
		}
		return border;
	}


	private void undo() {
		// TODO not implemented
		model.clearSelectedTraces();
		buttonUndo.setDisable(true);
	}

	private void applyCropLines() {
		model.clearSelectedTraces();

		MapField field = new MapField(mapField);
		field.setZoom(28);

		List<Point2D> cropArea = getScreenPoligon(field);

		for (SgyFile file : model.getFileManager().getFiles()) {
			traceTransform.cropLines(file, field, cropArea);
		}
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
		Chart chart = model.getFileChart(file);
		if (chart != null) {
			model.clearSelectedTrace(chart);
		}

		traceTransform.splitLine(file, splitIndex);
	}

	public static List<BaseObject> copyAuxObjects(SgyFile file, SgyFile sgyFile, int begin, int end) {
		List<BaseObject> auxObjects = new ArrayList<>();				
		for (BaseObject au : file.getAuxElements()) {
			if (au.isFit(begin, end)) {
				BaseObject copy = au.copy(begin);
				if (copy != null) {
					auxObjects.add(copy);
				}
			}
		}
		return auxObjects;
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
}
