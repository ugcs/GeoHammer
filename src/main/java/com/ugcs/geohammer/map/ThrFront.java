package com.ugcs.geohammer.map;

import java.awt.image.BufferedImage;

import com.ugcs.geohammer.model.MapField;

public class ThrFront {
	
	private final BufferedImage img;
	
	private final MapField field;

	public ThrFront(BufferedImage img, MapField field) {
		if (img == null) {
			throw new RuntimeException("img == null");
		}
		
		this.img = img;
		this.field = field;
	}

	public MapField getField() {
		return field;
	}

	public BufferedImage getImg() {
		return img;
	}
	
}
