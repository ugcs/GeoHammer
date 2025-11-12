package com.ugcs.geohammer.model.element;

import javafx.geometry.Point2D;

import java.awt.Dimension;
import java.awt.Rectangle;

public class AlignRect {
	
	int hor;
	int ver;
	
	public static final AlignRect CENTER = new AlignRect(0, 0);
	
	public AlignRect(int h, int v) {
		this.hor = h;
		this.ver = v;
	}
	
	public Rectangle getRect(Point2D point, Dimension dim) {
		
		int rh = dim.width / 2;
		int rv = dim.height / 2;
		
		Rectangle rect = new Rectangle((int) point.getX() - rh + rh * hor,
				(int) point.getY() - rv + rv * ver,
				dim.width, dim.height);
		return rect;		
	}
}
