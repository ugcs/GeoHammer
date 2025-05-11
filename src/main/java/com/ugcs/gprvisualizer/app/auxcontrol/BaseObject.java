package com.ugcs.gprvisualizer.app.auxcontrol;

import java.awt.*;
import java.util.List;

import com.ugcs.gprvisualizer.app.ScrollableData;
import javafx.geometry.Point2D;
import org.json.simple.JSONObject;

import com.github.thecoldwine.sigrun.common.ext.MapField;
import com.github.thecoldwine.sigrun.common.ext.VerticalCutPart;
import org.jspecify.annotations.Nullable;
//import com.ugcs.gprvisualizer.app.MouseHandler;

public interface BaseObject {//extends MouseHandler {
	
	default void drawOnMap(Graphics2D g2, MapField mapField) {

	}
	
	default void drawOnCut(Graphics2D g2, ScrollableData profField) {

	}
	
	default boolean isPointInside(Point2D localPoint, ScrollableData profField) {
		return false;
	}

	default void signal(@Nullable Object obj) {
	}
	
	default List<BaseObject> getControls() {
		return List.of();
	}
	
	default boolean saveTo(JSONObject json) {
		return false;
	}
	
	default boolean mousePressHandle(Point2D point, MapField mapField) {
		return false;
	}
		
	default BaseObject copy(int offset, VerticalCutPart verticalCutPart) {
		throw new RuntimeException("not implemented");
	}
	
	default boolean isFit(int begin, int end) {
		return false;
	}
	
	void setSelected(boolean selected);
	
	boolean isSelected();

	default boolean mousePressHandle(Point2D localPoint, ScrollableData profField) {
		return false;
	}

	default boolean mouseReleaseHandle(Point2D localPoint, ScrollableData profField) {
		return false;
	}

	default boolean mouseMoveHandle(Point2D point, ScrollableData profField) {
		return false;
	}

}
