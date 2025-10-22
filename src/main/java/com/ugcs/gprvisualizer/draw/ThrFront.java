package com.ugcs.gprvisualizer.draw;

import java.awt.image.BufferedImage;

import com.github.thecoldwine.sigrun.common.ext.MapField;

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
