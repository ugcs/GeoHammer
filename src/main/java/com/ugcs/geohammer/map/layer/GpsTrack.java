package com.ugcs.geohammer.map.layer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.ugcs.geohammer.map.RenderQueue;
import com.ugcs.geohammer.model.MapField;
import com.ugcs.geohammer.view.ResourceImageHolder;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.chart.Chart;
import com.ugcs.geohammer.model.event.FileClosedEvent;
import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.model.event.FileOpenedEvent;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
import com.ugcs.geohammer.model.event.WhatChanged;
import com.ugcs.geohammer.math.DouglasPeucker;
import com.ugcs.geohammer.model.IndexRange;
import javafx.geometry.Point2D;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ugcs.geohammer.model.Model;

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
	private final RenderQueue q;

	public GpsTrack(Model model) {
		this.model = model;
		this.q = new RenderQueue(model) {
			public void draw(BufferedImage image, MapField field) {
				Graphics2D g2 = (Graphics2D) image.getGraphics();
				g2.translate(image.getWidth() / 2, image.getHeight() / 2);
				drawTrack(g2, field);
			}
			public void onReady() {
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
	public void setSize(Dimension size) {
		q.setRenderSize(size);
	}

	@Override
	public void draw(Graphics2D g2, MapField currentField) {
		if (currentField.getSceneCenter() == null || !isActive()) {
			return;
		}

		q.drawWithTransform(g2, currentField, q.getLastFrame());
	}
	
	public void drawTrack(Graphics2D g2, MapField field) {
		if (!field.isActive()) {
			return;
		}

		// Make a copy to avoid concurrent modification
		SgyFile[] files = model.getFileManager().getFiles().toArray(new SgyFile[0]);
		for (SgyFile sgyFile : files) {
			drawTraceLines(g2, field, sgyFile);
		}
	}

	private void drawTraceLines(Graphics2D g2, MapField field, SgyFile file) {
		boolean isSelectedFile = Objects.equals(file, model.getCurrentFile());

		Chart chart = model.getChart(file);
		Integer selectedLineIndex = isSelectedFile && chart != null
				? chart.getSelectedLineIndex()
				: null;

		var ranges = file.getLineRanges();
		for (Map.Entry<Integer, IndexRange> e: ranges.entrySet()) {
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

			IndexRange range = e.getValue();
			var traces = file.getGeoData().subList(range.from(), range.to());
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
			q.submit();
		}
	}

	@EventListener
	private void fileOpened(FileOpenedEvent fileOpenedEvent) {
		q.submit();
	}

	@EventListener
	private void fileClosed(FileClosedEvent fileClosedEvent) {
		q.submit();
	}

	@EventListener
	private void fileSelected(FileSelectedEvent fileSelectedEvent) {
		q.submit();
	}

	@Override
	public List<Node> getToolNodes() {
		return Arrays.asList(showLayerCheckbox);
	}
}
