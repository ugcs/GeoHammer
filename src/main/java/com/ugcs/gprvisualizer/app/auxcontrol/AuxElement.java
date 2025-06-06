package com.ugcs.gprvisualizer.app.auxcontrol;

import java.awt.Color;
import java.awt.Graphics2D;

import com.github.thecoldwine.sigrun.common.ext.MapField;
import com.github.thecoldwine.sigrun.common.ext.ProfileField;
import com.github.thecoldwine.sigrun.common.ext.Trace;
import com.github.thecoldwine.sigrun.common.ext.TraceSample;
import com.ugcs.gprvisualizer.app.ScrollableData;
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
		
		TraceSample ts = new TraceSample(traceStart.getIndexInFile(),
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
