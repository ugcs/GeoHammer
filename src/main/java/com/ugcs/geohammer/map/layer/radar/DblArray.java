package com.ugcs.geohammer.map.layer.radar;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

public class DblArray {

	private final int width;

	private final int height;
	
	private final double[][] array;
	
	public DblArray(int width, int height) {
		this.width = width;
		this.height = height;
		array = new double[width][height];
	}
	
	public void clear() {
	    for (int x = 0; x < width; x++) {
	    	for (int y = 0; y < height; y++) {
	    		array[x][y] = 0;
	    	}
	    }		
	}
	
	public void drawCircle(int x, int y, int r, double s) {
		int r2 = r * r;
		int y1 = normY(y - r);
		int y2 = normY(y + r);

		for (int vy = y1; vy < y2; vy++) {
			int dy = Math.abs(y - vy);
			int dx = (int) (Math.sqrt(r * r - dy * dy));
			int x1 = normX(x - dx);
			int x2 = normX(x + dx);
			
			for (int i = x1; i < x2; i++) {
				int curx = Math.abs(i - x);
				int curr2 = curx * curx + dy * dy;
				array[i][vy] = Math.max(array[i][vy], s * (r2 - curr2) / r2);
			}			
		}		
	}
	
	int normX(int x) {
		return Math.clamp(x, 0, width);
	}
	
	int normY(int y) {
		return Math.clamp(y, 0, height);
	}
	
	public void drawLine(int x1, int x2, int cx, int r,  int y, double s) {
		if (y < 0 || y >= height) {
			return;
		}

		x1 = Math.clamp(x1, 0, width);
		x2 = Math.clamp(x2, 0, width);

		for (int i = x1; i < x2; i++) {
			array[i][y] = Math.max(array[i][y], s);
		}		
	}
	
	public BufferedImage toImg(BufferedImage image, int[] palette) {
	    int[] buffer = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();	    
	    
	    for (int x = 0; x < width; x++) {
	    	for (int y = 0; y < height; y++) {
	    		if (array[x][y] > 4) {
	    			buffer[x + y * width] = palette[(int) (array[x][y])];
	    		}	    	
	    	}
	    }

	    return image;
	}

	public int getWidth() {
		return width;
	}
	
	public int getHeight() {
		return height;		
	}
}
