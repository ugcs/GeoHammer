package com.ugcs.gprvisualizer.app.auxcontrol;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;

import com.ugcs.gprvisualizer.app.GPRChart;
import com.ugcs.gprvisualizer.app.ScrollableData;
import javafx.geometry.Point2D;

import com.github.thecoldwine.sigrun.common.ext.ProfileField;
import com.github.thecoldwine.sigrun.common.ext.TraceSample;
import com.ugcs.gprvisualizer.app.AppContext;
import com.ugcs.gprvisualizer.event.WhatChanged;

public class DepthStart extends BaseObjectImpl {

	int horM;
	int verM;
	int offsetX;
	int offsetY;
		
	Shape shape;
	//ProfileField profField;
	
	public DepthStart(Shape shape) {
		this.shape = shape;
		horM = shape.getBounds().width;
		verM = shape.getBounds().height;
		offsetX = shape.getBounds().x;
		offsetY = shape.getBounds().y;
	}
	
	@Override
	public boolean mousePressHandle(Point2D localPoint, ScrollableData profField) {
		if (isPointInside(localPoint, profField)) {
			return true;
		}
		return false;
	}

	@Override
	public BaseObject copy(int traceOffset) {
		return null;
	}


	@Override
	public boolean mouseMoveHandle(Point2D point, ScrollableData scrollable) {
		TraceSample ts = scrollable.screenToTraceSample(point);
		if (scrollable instanceof GPRChart gprChart) {
			controlToSettings(ts, gprChart.getField());
		}
		AppContext.model.publishEvent(new WhatChanged(this, WhatChanged.Change.adjusting));
		return true;
	}

	protected void controlToSettings(TraceSample ts, ProfileField profField) {
		int max = profField.getMaxHeightInSamples();
		profField.getProfileSettings().setLayer(Math.min(max - profField.getProfileSettings().hpage,
			Math.max(0, ts.getSample())));
	}

	@Override
	public void drawOnCut(Graphics2D g2, ScrollableData scrollableData) {
		if (scrollableData instanceof GPRChart gprChart) {
			Rectangle leftMainRect = gprChart.getField().getClipLeftMainRect();
			int depthHeight = verM;
			Rectangle rectangle = new Rectangle(leftMainRect.x, leftMainRect.y - depthHeight,
					leftMainRect.width, leftMainRect.height + 2 * depthHeight);
			setClip(g2, rectangle);

			Point2D p = getCenter(gprChart);

			g2.setColor(Color.BLUE);

			g2.translate(p.getX(), p.getY());
			g2.fill(shape);

			if (isSelected()) {
				g2.setColor(Color.green);
				g2.setStroke(FoundPlace.SELECTED_STROKE);
				g2.draw(shape);
			}

			g2.translate(-p.getX(), -p.getY());
		}
	}

	@Override
	public boolean isPointInside(Point2D localPoint, ScrollableData profField) {
		Rectangle rect = getRect(profField);
		return rect.contains(localPoint.getX(), localPoint.getY());
	}
	
	//@Override
	private Rectangle getRect(ScrollableData profField) {
		Point2D scr = getCenter(profField);
		Rectangle rect = new Rectangle((int) scr.getX() + offsetX,(int) scr.getY() + offsetY, horM, verM);
		return rect;
	}

	protected Point2D getCenter(ScrollableData scrollableData) {
		if (scrollableData instanceof GPRChart gprChart) {
			var profField = gprChart.getField();
			Point2D scr = scrollableData.traceSampleToScreen(new TraceSample(
					0, profField.getProfileSettings().getLayer()));
			return new Point2D(profField.getVisibleStart(), scr.getY());
		} else {
			return scrollableData.traceSampleToScreen(new TraceSample(0, 0));
		}
	}
}