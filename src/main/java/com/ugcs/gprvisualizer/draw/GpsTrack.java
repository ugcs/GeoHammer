package com.ugcs.gprvisualizer.draw;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;

import com.github.thecoldwine.sigrun.common.ext.CsvFile;
import com.github.thecoldwine.sigrun.common.ext.LatLon;
import com.github.thecoldwine.sigrun.common.ext.MapField;
import com.github.thecoldwine.sigrun.common.ext.ResourceImageHolder;
import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.github.thecoldwine.sigrun.common.ext.Trace;
import com.ugcs.gprvisualizer.app.MapView;
import com.ugcs.gprvisualizer.event.FileOpenedEvent;
import com.ugcs.gprvisualizer.event.WhatChanged;
import com.ugcs.gprvisualizer.utils.Range;
import javafx.geometry.Point2D;
import org.springframework.beans.factory.InitializingBean;
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

	private EventHandler<ActionEvent> showMapListener = new EventHandler<ActionEvent>() {
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

		g2.setStroke(new BasicStroke(1.0f));
		
		double sumdist = 0;
		
		LatLon ll = field.screenTolatLon(
				new Point2D(0, 5));
		
		// meter to cm
		double threshold =				
				field.getSceneCenter().getDistance(ll) * 100.0;
		
		//
		g2.setColor(Color.RED);
		
		for (SgyFile sgyFile : model.getFileManager().getGprFiles()) {
			sumdist = drawTraceLines(g2, field, sumdist, threshold, sgyFile);
		}

		for (SgyFile sgyFile : model.getFileManager().getCsvFiles()) {
			sumdist = drawTraceLines(g2, field, sumdist, threshold, sgyFile);
		}
	}

	private double drawTraceLines(Graphics2D g2, MapField field, double sumdist, double threshold, SgyFile sgyFile) {
		if (sgyFile instanceof CsvFile csvFile) {
			var ranges = csvFile.getLineRanges();
			for(Range range: ranges.values()) {
				Point2D prevPoint = null;
				var traces = csvFile.getTraces().subList(range.getMin().intValue(), range.getMax().intValue());
				sumdist = renderTraceLines(g2, field, sumdist, threshold, traces, prevPoint);
			}
		} else {
			Point2D prevPoint = null;
			var traces = sgyFile.getTraces();
			sumdist = renderTraceLines(g2, field, sumdist, threshold, traces, prevPoint);
		}
		return sumdist;
	}

	private static double renderTraceLines(Graphics2D g2, MapField field, double sumdist, double threshold, List<Trace> traces, Point2D prevPoint) {
		for(Trace trace : traces) {
			if (prevPoint == null) {
				prevPoint = field.latLonToScreen(trace.getLatLon());
				sumdist = 0;
			} else {
				//prev point exists
				sumdist += trace.getPrevDist();
				if (sumdist >= threshold) {
					Point2D pointNext = field.latLonToScreen(trace.getLatLon());
					g2.drawLine((int) prevPoint.getX(),
							(int) prevPoint.getY(),
							(int) pointNext.getX(),
							(int) pointNext.getY());
					prevPoint = pointNext;
					sumdist = 0;
				}
			}
		}
		return sumdist;
	}

	@EventListener
	private void somethingChanged(WhatChanged changed) {
		if (changed.isTraceCut() 
				|| changed.isTraceValues() 
				|| changed.isZoom()
				|| changed.isAdjusting() 
				|| changed.isMapscroll() 
				|| changed.isWindowresized()) {
			q.add();
		}
	}

	@EventListener
	private void fileOpened(FileOpenedEvent fileOpenedEvent) {
		q.add();
	}

	@Override
	public List<Node> getToolNodes() {
		return Arrays.asList(showLayerCheckbox);
	}
	
}
