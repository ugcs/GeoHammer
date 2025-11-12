package com.ugcs.geohammer.model.element;

import java.awt.*;
import java.util.List;

import com.ugcs.geohammer.chart.ScrollableData;
import javafx.geometry.Point2D;
import org.json.simple.JSONObject;

import com.ugcs.geohammer.model.MapField;
import org.jspecify.annotations.Nullable;

public interface BaseObject {
	
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
		
	default BaseObject copy(int traceOffset) {
		return null;
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
