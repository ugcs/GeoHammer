package com.ugcs.geohammer.model.element;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Stroke;

import com.ugcs.geohammer.model.SelectedTrace;
import com.ugcs.geohammer.model.TraceKey;
import com.ugcs.geohammer.chart.gpr.GPRChart;
import com.ugcs.geohammer.chart.ScrollableData;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.view.Views;
import javafx.geometry.Point2D;
import org.json.simple.JSONObject;

import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.model.MapField;
import com.ugcs.geohammer.view.ResourceImageHolder;
import com.ugcs.geohammer.model.Model;

public class ClickPlace extends PositionalObject {

	public static final Color AUTO_COLOR = new Color(0xFF6B6B);

	public static final Color USER_COLOR = new Color(0xC40000);

	public static final Image AUTO_IMAGE_BODY = Views.tintImage(ResourceImageHolder.IMG_GPS_BODY, AUTO_COLOR);

	public static final Image USER_IMAGE_BODY = Views.tintImage(ResourceImageHolder.IMG_GPS_BODY, USER_COLOR);

	private final static int R_HOR = ResourceImageHolder.IMG_GPS.getWidth(null) / 2;

	private final static int R_VER = ResourceImageHolder.IMG_GPS.getHeight(null) / 2;

	private static final float[] dash1 = {7.0f, 2.0f};
	static Stroke VERTICAL_STROKE = 	
			new BasicStroke(1.0f,
                BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_MITER,
                10.0f, dash1, 0.0f);

	private final SelectedTrace selectedTrace;

	public ClickPlace(SelectedTrace selectedTrace) {
		Check.notNull(selectedTrace);
		this.selectedTrace = selectedTrace;
	}

	public TraceKey getTrace() {
		return selectedTrace.trace();
	}

	@Override
	public int getTraceIndex() {
		return selectedTrace.trace().getIndex();
	}

	@Override
	public void offset(int traceOffset) {
		selectedTrace.trace().offset(traceOffset);
	}

	@Override
	public boolean mousePressHandle(Point2D point, MapField mapField) {
		return false;
	}

	@Override
	public BaseObject copy(int traceOffset) {
		TraceKey traceKey = selectedTrace.trace();
		TraceKey newTraceKey = new TraceKey(traceKey.getFile(), traceKey.getIndex() - traceOffset);
		SelectedTrace newSelectedTrace = new SelectedTrace(newTraceKey, selectedTrace.selectionType());
		return new ClickPlace(newSelectedTrace);
	}

	@Override
	public void drawOnMap(Graphics2D g2, MapField mapField) {

		Rectangle rect = getRect(mapField);

		g2.translate(rect.x, rect.y);
		g2.drawImage(getImageBodyBySelectionType(), 0, 0, null);
		g2.drawImage(ResourceImageHolder.IMG_GPS, 0, 0, null);
		g2.translate(-rect.x, -rect.y);
	}

	@Override
	public void drawOnCut(Graphics2D g2, ScrollableData scrollableData) {
		if (selectedTrace.trace().getFile() instanceof CsvFile) {
			return;
		}

		if (scrollableData instanceof GPRChart gprChart) {
			var profField = gprChart.getField();
			setClip(g2, profField.getClipTopMainRect());

			Rectangle rect = getRect(gprChart);

			g2.translate(rect.x, rect.y);
			g2.drawImage(getImageBodyBySelectionType(), 0, 0, null);
			g2.drawImage(ResourceImageHolder.IMG_GPS, 0, 0, null);

			g2.setStroke(VERTICAL_STROKE);
			g2.setColor(Color.blue);
			g2.setXORMode(Color.gray);
			g2.drawLine(R_HOR, R_VER * 2 , R_HOR,
			gprChart.sampleToScreen(profField.getMaxHeightInSamples()) - Model.TOP_MARGIN + R_VER * 2);
			g2.setPaintMode();
			g2.translate(-rect.x, -rect.y);
		}
	}

	private Image getImageBodyBySelectionType() {
		return switch (selectedTrace.selectionType()) {
			case USER -> USER_IMAGE_BODY;
			case AUTO -> AUTO_IMAGE_BODY;
		};
	}

	private Rectangle getRect(GPRChart gprChart) {
		int x = gprChart.traceToScreen(selectedTrace.trace().getIndex());

		return new Rectangle(
				x - R_HOR, Model.TOP_MARGIN - R_VER * 2,
				R_HOR * 2, R_VER * 2);
	}
	
	private Rectangle getRect(MapField mapField) {
		//Trace tr = getTrace();		
		Point2D p =  mapField.latLonToScreen(selectedTrace.trace().getLatLon());

		return new Rectangle(
				(int) p.getX() - R_HOR, (int) p.getY() - R_VER * 2,
				R_HOR * 2, R_VER * 2);
	}

	@Override
	public boolean saveTo(JSONObject json) {
		return false;
	}
}
