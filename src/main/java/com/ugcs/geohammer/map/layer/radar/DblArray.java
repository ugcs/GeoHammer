package com.ugcs.geohammer.map.layer.radar;

import com.ugcs.geohammer.util.PaletteBuilder;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

public class DblArray {

	int width;
	int height;
	
	private double[][] array;
	
	public static int[] paletteAmp = new PaletteBuilder().build();
	public static int[] paletteAlg = new PaletteBuilder().build2();
	
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
		return x < 0 ? 0 : (x > width ? width : x);
	}
	
	int normY(int y) {
		return y < 0 ? 0 : (y > height ? height : y);
	}
	
	public void drawLine(int x1, int x2, int cx, int r,  int y, double s) {
		
		if (y < 0 || y >= height) {
			return;
		}
		
		x1 = x1 < 0 ? 0 : x1;
		x1 = x1 > width ? width : x1;

		x2 = x2 < 0 ? 0 : x2;
		x2 = x2 > width ? width : x2;
		
		for (int i = x1; i < x2; i++) {
			array[i][y] = Math.max(array[i][y], s);
		}		
	}
	
	public BufferedImage toImg(int[] palette) {
		
	    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
	    
	    return toImg(image, palette);
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

	    //tmp show palette
	    //if (Sout.developMode) {
		    //showGradientPalatte(palette, buffer);
	    //}
	    
	    return image;
	}

	public void showGradientPalatte(int[] palette, int[] buffer) {
		for (int x = 0; x < width; x++) {
			for (int y = 30; y < 50; y++) {
				
				buffer[x + y * width] = palette[(int) (x * 200 / width)];
			}
		}
	}
	
	private void saveImg(BufferedImage bufferedImage, String fname) {
		File file = new File(fname);
		try {
			ImageIO.write(bufferedImage, "png", file);

		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public int getWidth() {
		return width;
	}
	
	public int getHeight() {
		return height;		
	}
}
