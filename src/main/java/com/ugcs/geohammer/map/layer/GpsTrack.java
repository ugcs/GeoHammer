package com.ugcs.geohammer.map.layer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.ugcs.geohammer.map.MapColors;
import com.ugcs.geohammer.map.RenderQueue;
import com.ugcs.geohammer.model.ActivationPolicy;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.model.MapField;
import com.ugcs.geohammer.model.ToolNode;
import com.ugcs.geohammer.view.Colors;
import com.ugcs.geohammer.view.ResourceImageHolder;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.chart.Chart;
import com.ugcs.geohammer.model.event.FileClosedEvent;
import com.ugcs.geohammer.model.event.FileCollapsedEvent;
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
			if (model.isCollapsed(sgyFile)) {
				continue;
			}
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
			Color color = MapColors.OTHER_TRACK;
			float strokeWidth = 1f;
			if (isSelectedFile) {
				Integer lineIndex = e.getKey();
				if (selectedLineIndex != null
						&& selectedLineIndex.equals(lineIndex)
						&& ranges.size() > 1) {
					// selected line
					color = MapColors.SELECTED_LINE;
					strokeWidth = 3f;
				} else {
					// selected file
					color = MapColors.TRACK;
					strokeWidth = 2f;
				}
			}

			IndexRange range = e.getValue();
			var traces = file.getGeoData().subList(range.from(), range.to());
			renderTraceLines(g2, field, traces, color, strokeWidth);
		}
	}

	private static void renderTraceLines(Graphics2D g2, MapField field, List<GeoData> traces, Color color, float strokeWidth) {
		List<Point2D> points = new ArrayList<>(traces.size());
		for (GeoData trace : traces) {
			LatLon latLon = trace.getLatLon();
			if (latLon == null) {
				continue;
			}
			points.add(field.latLonToScreen(latLon));
		}
		List<Integer> selected = DouglasPeucker.approximatePolyline(points,
				APPROXIMATION_THRESHOLD, 2);
		if (selected.size() < 2) {
			return;
		}

		// one path per line: a translucent stroke paints segment joints once
		Path2D path = new Path2D.Double();
		Point2D first = points.get(selected.getFirst());
		path.moveTo(first.getX(), first.getY());
		for (int i = 1; i < selected.size(); i++) {
			Point2D p = points.get(selected.get(i));
			path.lineTo(p.getX(), p.getY());
		}

		g2.setColor(Colors.opaque(color.darker().darker().darker(), 0.38f));
		g2.setStroke(new BasicStroke(strokeWidth + 2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g2.draw(path);

		g2.setColor(color);
		g2.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g2.draw(path);
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

	@EventListener
	private void onFileCollapsed(FileCollapsedEvent event) {
		q.submit();
	}

	@Override
	public List<ToolNode> getToolNodes() {
		return List.of(
				new ToolNode(showLayerCheckbox, ActivationPolicy.fileSelected())
		);
	}
}
