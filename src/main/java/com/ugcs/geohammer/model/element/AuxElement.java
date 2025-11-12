package com.ugcs.geohammer.model.element;

import java.awt.Color;
import java.awt.Graphics2D;

import com.ugcs.geohammer.model.MapField;
import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.model.TraceSample;
import com.ugcs.geohammer.chart.ScrollableData;
import javafx.geometry.Point2D;

public class AuxElement {

	private Trace traceStart;
	private Integer sampleStart;
	private static final int r = 5;		
	
	public AuxElement(Trace traceStart, Integer sampleStart) {
		this.traceStart = traceStart;
		this.sampleStart = sampleStart;
	}
	
	public void drawOnCut(Graphics2D g2, ScrollableData field) {
		
		TraceSample ts = new TraceSample(traceStart.getIndex(),
				sampleStart != null ? sampleStart : 0);
		Point2D scr = field.traceSampleToScreen(ts);
		
		g2.setColor(Color.MAGENTA);
		g2.fillOval((int) scr.getX() - r, (int) scr.getY() - r, r * 2, r * 2);
	}

	public void drawOnMap(Graphics2D g2, MapField field) {
		Point2D scr = field.latLonToScreen(traceStart.getLatLon());
		g2.fillOval((int) scr.getX() - r, (int) scr.getY() - r, r * 2, r * 2);		
	}

	public Trace getTraceStart() {
		return traceStart;
	}

	public void setTraceStart(Trace t) {
		traceStart = t;
	}
	
	public Integer getSampleStart() {
		return sampleStart;
	}

	public void setSampleStart(Integer s) {
		sampleStart = s;
	}
}
