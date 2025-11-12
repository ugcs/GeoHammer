package com.ugcs.geohammer.map.layer;

import java.awt.Graphics2D;

import com.ugcs.geohammer.model.MapField;
import com.ugcs.geohammer.model.ToolProducer;
import javafx.geometry.Point2D;

public interface Layer extends ToolProducer {

	void draw(Graphics2D g2, MapField field);
			
	default boolean mousePressed(Point2D point) {
		return false;
	};
	
	default boolean mouseRelease(Point2D point) {
		return false;
	};
	
	default boolean mouseMove(Point2D point) {
		return false;
	};

}