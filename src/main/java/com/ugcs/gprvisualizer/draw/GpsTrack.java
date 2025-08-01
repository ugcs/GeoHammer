package com.ugcs.gprvisualizer.draw;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.github.thecoldwine.sigrun.common.ext.MapField;
import com.github.thecoldwine.sigrun.common.ext.ResourceImageHolder;
import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.ugcs.gprvisualizer.app.Chart;
import com.ugcs.gprvisualizer.app.MapView;
import com.ugcs.gprvisualizer.app.events.FileClosedEvent;
import com.ugcs.gprvisualizer.app.parcers.GeoData;
import com.ugcs.gprvisualizer.event.FileOpenedEvent;
import com.ugcs.gprvisualizer.event.FileSelectedEvent;
import com.ugcs.gprvisualizer.event.WhatChanged;
import com.ugcs.gprvisualizer.math.DouglasPeucker;
import com.ugcs.gprvisualizer.utils.Range;
import javafx.geometry.Point2D;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ugcs.gprvisualizer.gpr.Model;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;

@Component
public class GpsTrack extends BaseLayer {

	// approximation threshold in pixels
	private static final double APPROXIMATION_THRESHOLD = 1.5;

	private final Model model;
	private final ThrQueue q;

	public GpsTrack(Model model, MapView mapView) {
		this.model = model;
		this.q = new ThrQueue(model, mapView) {
			protected void draw(BufferedImage backImg, MapField field) {
				Graphics2D g2 = (Graphics2D) backImg.getGraphics();
				g2.translate(backImg.getWidth() / 2, backImg.getHeight() / 2);
				drawTrack(g2, field);
			}
			public void ready() {
				getRepaintListener().repaint();
			}
		};
	}

	private EventHandler<ActionEvent> showMapListener = new EventHandler<>() {
		@Override
		public void handle(ActionEvent event) {
			setActive(showLayerCheckbox.isSelected());
			getRepaintListener().repaint();				
		}
	};
	
	private final ToggleButton showLayerCheckbox = ResourceImageHolder.setButtonImage(ResourceImageHolder.PATH, new ToggleButton());

	{
		showLayerCheckbox.setTooltip(new Tooltip("Toggle GPS track layer"));
		showLayerCheckbox.setSelected(true);
		showLayerCheckbox.setOnAction(showMapListener);
	}
		
	@Override
	public void draw(Graphics2D g2, MapField currentField) {
		if (currentField.getSceneCenter() == null || !isActive()) {
			return;
		}

		q.drawImgOnChangedField(g2, currentField, q.getFront());
	}
	
	public void drawTrack(Graphics2D g2, MapField field) {
		if (!field.isActive()) {
			return;
		}

		for (SgyFile sgyFile : model.getFileManager().getFiles()) {
			drawTraceLines(g2, field, sgyFile);
		}
	}

	private void drawTraceLines(Graphics2D g2, MapField field, SgyFile file) {
		boolean isSelectedFile = Objects.equals(file, model.getCurrentFile());

		Chart chart = model.getFileChart(file);
		Integer selectedLineIndex = isSelectedFile && chart != null
				? chart.getSelectedLineIndex()
				: null;

		var ranges = file.getLineRanges();
		for (Map.Entry<Integer, Range> e: ranges.entrySet()) {
			if (isSelectedFile) {
				Integer lineIndex = e.getKey();
				if (selectedLineIndex != null
						&& selectedLineIndex.equals(lineIndex)
						&& ranges.size() > 1) {
					// selected line
					g2.setStroke(new BasicStroke(3.0f));
					g2.setColor(new Color(0xCFE34A));
				} else {
					// selected file
					g2.setStroke(new BasicStroke(2.0f));
					g2.setColor(new Color(0xFF2816));
				}
			} else {
				// not selected
				g2.setStroke(new BasicStroke(1.0f));
				g2.setColor(new Color(0xFA7D6E));
			}

			Range range = e.getValue();
			var traces = file.getGeoData().subList(range.getMin().intValue(), range.getMax().intValue() + 1);
			renderTraceLines(g2, field, traces);
		}
	}

	private static void renderTraceLines(Graphics2D g2, MapField field, List<GeoData> traces) {
		List<Point2D> points = new ArrayList<>(traces.size());
		for (GeoData trace : traces) {
			Point2D point = field.latLonToScreen(trace.getLatLon());
			points.add(point);
		}
		List<Integer> selected = DouglasPeucker.approximatePolyline(points,
				APPROXIMATION_THRESHOLD, 2);
		if (selected.size() < 2) {
			return;
		}
		for (int i = 1; i < selected.size(); i++) {
			Point2D p1 = points.get(selected.get(i - 1));
			Point2D p2 = points.get(selected.get(i));
			g2.drawLine(
					(int)p1.getX(),
					(int)p1.getY(),
					(int)p2.getX(),
					(int)p2.getY());
		}
	}

	@EventListener
	private void somethingChanged(WhatChanged changed) {
		if (changed.isTraceCut() 
				|| changed.isTraceValues() 
				|| changed.isZoom()
				|| changed.isAdjusting() 
				|| changed.isMapscroll() 
				|| changed.isWindowresized()
				|| changed.isJustdraw()
				|| changed.isTraceSelected()) {
			q.add();
		}
	}

	@EventListener
	private void fileOpened(FileOpenedEvent fileOpenedEvent) {
		q.add();
	}

	@EventListener
	private void fileClosed(FileClosedEvent fileClosedEvent) {
		q.add();
	}

	@EventListener
	private void fileSelected(FileSelectedEvent fileSelectedEvent) {
		q.add();
	}

	@Override
	public List<Node> getToolNodes() {
		return Arrays.asList(showLayerCheckbox);
	}
}
