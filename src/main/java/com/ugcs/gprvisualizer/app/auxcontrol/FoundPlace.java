package com.ugcs.gprvisualizer.app.auxcontrol;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.util.List;

import com.github.thecoldwine.sigrun.common.ext.*;
import com.ugcs.gprvisualizer.app.GPRChart;
import com.ugcs.gprvisualizer.app.ScrollableData;
import com.ugcs.gprvisualizer.app.SensorLineChart;
import com.ugcs.gprvisualizer.event.WhatChanged;
import com.ugcs.gprvisualizer.utils.Check;
import javafx.geometry.Point2D;

import com.ugcs.gprvisualizer.app.AppContext;
import com.ugcs.gprvisualizer.draw.ShapeHolder;
import com.ugcs.gprvisualizer.gpr.Model;
import org.jspecify.annotations.Nullable;

public class FoundPlace extends BaseObjectWithModel implements Positional {

	//static int R_HOR = ResourceImageHolder.IMG_SHOVEL.getWidth(null) / 2;
	//static int R_VER = ResourceImageHolder.IMG_SHOVEL.getHeight(null) / 2;
	static int R_HOR_M = ShapeHolder.flag.getBounds().width / 2;
	static int R_VER_M = ShapeHolder.flag.getBounds().height / 2;

	public static Stroke SELECTED_STROKE = new BasicStroke(2.0f);
	
	private static final float[] dash1 = {7.0f, 2.0f};
	private static final Stroke VERTICAL_STROKE = 	
			new BasicStroke(1.0f,
                BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_MITER,
                10.0f, dash1, 0.0f);

	private final Color flagColor = Color.getHSBColor((float) Math.random(), 0.9f, 0.97f);
	private TraceKey trace;

	public TraceKey getTrace() {
		return trace;
	}

	@Override
	public int getTraceIndex() {
		return trace.getIndex();
	}

	@Override
	public void offset(int traceOffset) {
		trace.offset(traceOffset);
	}

	public Color getFlagColor() {
		return flagColor;
	}
	
	public FoundPlace(TraceKey trace, Model model) {
		super(model);
		Check.notNull(trace);
		this.trace = trace;
	}

	@Override
	public boolean mousePressHandle(Point2D point, MapField field) {
		Rectangle r = getRect(field);
		if (r.contains(point.getX(), point.getY())) {
			ScrollableData scrollable;
			if (trace.getFile() instanceof CsvFile csvFile) {
				scrollable = model.getCsvChart(csvFile).get();
			} else {
				scrollable = model.getGprChart(trace.getFile());
			}
			int traceIndex = getTraceIndex();
			scrollable.setMiddleTrace(traceIndex);

			model.publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));

			coordinatesToStatus();
			return true;
		}
		return false;
	}

	public void coordinatesToStatus() {
		if (trace == null) {
			return;
		}
		LatLon latlon = trace.getLatLon();
		if (latlon != null) {
			AppContext.status.showProgressText(latlon.toString());
		}
	}
	
	@Override
	public boolean mousePressHandle(Point2D localPoint, ScrollableData profField) {
		if (profField instanceof SensorLineChart || isPointInside(localPoint, profField)) {
			model.getMapField().setSceneCenter(getLatLon());
			model.publishEvent(new WhatChanged(this, WhatChanged.Change.mapscroll));
			coordinatesToStatus();
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseMoveHandle(Point2D point, ScrollableData profField) {
		if (trace.getFile() instanceof TraceFile traceFile) {
			GPRChart chart = model.getGprChart(traceFile);
			if (chart != null) {
				TraceSample sample = profField.screenToTraceSample(point);
				List<Trace> traces = traceFile.getTraces();
				int traceIndex = Math.clamp(sample.getTrace(), 0, traces.size() - 1);
				trace = new TraceKey(traceFile, traceIndex);
				model.publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
			}
		}
		coordinatesToStatus();
		return true;
	}

	@Override
	public void drawOnMap(Graphics2D g2, MapField mapField) {
		Rectangle rect = getRect(mapField);
		
		g2.setColor(flagColor);
		
		g2.translate(rect.x, rect.y + rect.height);
		
		g2.fill(ShapeHolder.flag);
		
		g2.setColor(Color.BLACK);
		g2.draw(ShapeHolder.flag);
		g2.translate(-rect.x, -(rect.y + rect.height));
	}

	@Override
	public void drawOnCut(Graphics2D g2, ScrollableData scrollableData) {
		if (scrollableData instanceof GPRChart gprChart) {
			var profField = gprChart.getField();

			g2.setClip(profField.getClipTopMainRect().x,
					profField.getClipTopMainRect().y,
					profField.getClipTopMainRect().width,
					profField.getClipTopMainRect().height);

			Rectangle rect = getRect(gprChart);

			g2.setColor(flagColor);

			g2.translate(rect.x, rect.y + rect.height);
			g2.fill(ShapeHolder.flag);

			if (isSelected()) {
				g2.setColor(Color.green);
				g2.setStroke(SELECTED_STROKE);
				g2.draw(ShapeHolder.flag);

				g2.setStroke(VERTICAL_STROKE);
				g2.setColor(Color.blue);
				g2.setXORMode(Color.gray);
				int maxSamples = gprChart.getField().getMaxHeightInSamples();
				g2.drawLine(
						0, 0,
						0, gprChart.sampleToScreen(maxSamples) - Model.TOP_MARGIN);
				g2.setPaintMode();
			}

			g2.translate(-rect.x, -(rect.y + rect.height));
		}
	}
	
	private Rectangle getRect(ScrollableData scrollableData) {
		int x = scrollableData.traceToScreen(getTraceIndex());
		Rectangle rect = new Rectangle(x, 
				Model.TOP_MARGIN - R_VER_M * 2,
				R_HOR_M * 2,
				R_VER_M * 2);
		return rect;
	}
	
	private Rectangle getRect(MapField mapField) {
		Point2D p = mapField.latLonToScreen(trace.getLatLon());
		
		Rectangle rect = new Rectangle((int) p.getX(), (int) p.getY() - R_VER_M * 2, 
				R_HOR_M * 2, R_VER_M * 2);
		return rect;
	}

	@Override
	public boolean isPointInside(Point2D localPoint, ScrollableData profField) {
		Rectangle rect = getRect(profField);
		return rect.contains(localPoint.getX(), localPoint.getY());
	}

	@Override
	public BaseObject copy(int traceOffset) {
		TraceKey newTrace = new TraceKey(trace.getFile(), trace.getIndex() - traceOffset);
		return new FoundPlace(newTrace, model);
	}

	@Override
	public boolean isFit(int begin, int end) {
		int traceIndex = getTraceIndex();
		return traceIndex >= begin && traceIndex <= end;
	}

	public LatLon getLatLon() {
		return trace.getLatLon();
	}
}