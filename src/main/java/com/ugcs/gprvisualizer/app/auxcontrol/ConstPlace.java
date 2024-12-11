package com.ugcs.gprvisualizer.app.auxcontrol;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;

import com.ugcs.gprvisualizer.app.ScrollableData;
import com.ugcs.gprvisualizer.draw.ShapeHolder;
import com.ugcs.gprvisualizer.event.WhatChanged;
import javafx.geometry.Point2D;
import org.json.simple.JSONObject;

import com.github.thecoldwine.sigrun.common.ext.LatLon;
import com.github.thecoldwine.sigrun.common.ext.MapField;
import com.github.thecoldwine.sigrun.common.ext.ProfileField;
import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.github.thecoldwine.sigrun.common.ext.Trace;
import com.github.thecoldwine.sigrun.common.ext.VerticalCutPart;
import com.ugcs.gprvisualizer.app.AppContext;
import com.ugcs.gprvisualizer.gpr.Model;

public class ConstPlace extends BaseObjectImpl implements BaseObject {

	private final LatLon latLon;
	private final int traceInFile;
	private final VerticalCutPart offset;
	static int R_HOR = ShapeHolder.flag2.getBounds().width / 2;
	static int R_VER = ShapeHolder.flag2.getBounds().height / 2;

		
	public static ConstPlace loadFromJson(JSONObject json, Model model, SgyFile sgyFile) {
		int traceNum = (int) (long) (Long) json.get("trace");
		return new ConstPlace(traceNum, null, sgyFile.getOffset());
	}
	
	public ConstPlace(int trace, LatLon latLon,  VerticalCutPart offset) {
		this.offset = offset;
		this.latLon = latLon;
		this.traceInFile = trace;
	}

	@Override
	public boolean mousePressHandle(Point2D localPoint, ScrollableData profField) {
		if (isPointInside(localPoint, profField)) {
			AppContext.model.getMapField().setSceneCenter(getTrace().getLatLon());
			AppContext.model.publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
			return true;
		}
		return false;
	}

	@Override
	public BaseObject copy(int offset, VerticalCutPart verticalCutPart) {
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

	public Rectangle getRect(ProfileField profField) {

		if (offset == null) {
			return null;//new Rectangle(0,0,1,1);
		}
		int x = profField.traceToScreen(offset.localToGlobal(traceInFile));
				
		Rectangle rect = new Rectangle(x - R_HOR, R_VER, R_HOR * 2, R_VER * 2);
		return rect;
	}
	
	public Rectangle getRect(MapField mapField) {
		
		Point2D p =  mapField.latLonToScreen(latLon);

		Rectangle rect = new Rectangle((int) p.getX(), (int) p.getY() - R_VER * 2,
			R_HOR * 2, R_VER * 2);
		return rect;
	}

	private Trace getTrace() {
		return AppContext.model.getGprTraces()
				.get(offset.localToGlobal(traceInFile));
	}

	public LatLon getLatLon() {
		return latLon;
	}

}
