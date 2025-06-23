package com.ugcs.gprvisualizer.app.auxcontrol;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;

import com.ugcs.gprvisualizer.app.ScrollableData;
import com.ugcs.gprvisualizer.draw.ShapeHolder;
import com.ugcs.gprvisualizer.event.WhatChanged;
import javafx.geometry.Point2D;

import com.github.thecoldwine.sigrun.common.ext.LatLon;
import com.github.thecoldwine.sigrun.common.ext.MapField;
import com.ugcs.gprvisualizer.app.AppContext;

public class ConstPlace extends BaseObjectImpl implements Positional {

	static int R_HOR = ShapeHolder.flag2.getBounds().width / 2;
	static int R_VER = ShapeHolder.flag2.getBounds().height / 2;

	private final LatLon latLon;
	private int traceIndex;
	
	public ConstPlace(int traceIndex, LatLon latLon) {
		this.latLon = latLon;
		this.traceIndex = traceIndex;
	}

	public LatLon getLatLon() {
		return latLon;
	}

	@Override
	public int getTraceIndex() {
		return traceIndex;
	}

	@Override
	public void offset(int traceOffset) {
		traceIndex += traceOffset;
	}

	@Override
	public boolean mousePressHandle(Point2D localPoint, ScrollableData profField) {
		if (isPointInside(localPoint, profField)) {
			AppContext.model.getMapField().setSceneCenter(latLon);
			AppContext.model.publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
			return true;
		}
		return false;
	}

	@Override
	public BaseObject copy(int traceOffset) {
		return null;
	}

	@Override
	public void drawOnMap(Graphics2D g2, MapField mapField) {
		Rectangle rect = getRect(mapField);

		g2.setColor(Color.ORANGE);

		g2.translate(rect.x, rect.y + rect.height);

		g2.fill(ShapeHolder.flag3);

		g2.setColor(Color.BLACK);
		g2.draw(ShapeHolder.flag3);
		g2.translate(-rect.x, -(rect.y + rect.height));
	}
	
	public Rectangle getRect(MapField mapField) {
		Point2D p =  mapField.latLonToScreen(latLon);
		Rectangle rect = new Rectangle((int) p.getX(), (int) p.getY() - R_VER * 2,
			R_HOR * 2, R_VER * 2);
		return rect;
	}
}
