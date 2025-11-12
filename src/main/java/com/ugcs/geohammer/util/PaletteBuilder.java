package com.ugcs.geohammer.util;

public class PaletteBuilder {

	double delit = 25.0;
	int start = 0;
	
	public PaletteBuilder() {
		
	}

	public PaletteBuilder(double delit, int start) {
		this.delit = delit;
		
		this.start = start;
	}
	
	public int[] build() {
		
		int[] palette = new int[15000];
		
		for (int i = 0; i < palette.length; i++) {
			
			double t = ((double) i + start) / delit;
			
			int r = ((int) ((Math.cos(t * 1.50) + 1) / 2 * 255.0)) & 0xff;
			int g = ((int) ((Math.cos(t * 1.23) + 1) / 2 * 255.0)) & 0xff;
			int b = ((int) ((Math.cos(t * 1.00) + 1) / 2 * 255.0)) & 0xff;
			int alpha = (int) (i < 55.0 ? i / 55.0 * 180.0 : 180.0);
			
			palette[i] = r + (g << 8) + (b << 16) + (alpha << 24);
		}
		
		
		return palette;
	}

	public int[] build2() {
		
		int[] palette = new int[15000];
		
		for (int i = 0; i < palette.length; i++) {
			
			double t = ((double) i + start) / delit;
			
			int r = ((int) ((Math.sin(34 + t * 1.68) + 1.4) / 2.8 * 255.0)) & 0xff;
			int g = ((int) ((Math.cos(t * 1.43) + 1.1) / 2.8 * 255.0)) & 0xff;
			int b = ((int) ((Math.sin(0.32 + t * 0.88) + 1) / 2 * 255.0)) & 0xff;
			int alpha = (int) (i < 20.0 ? i / 20.0 * 180.0 : 180.0);
			
			palette[i] = (r << 16) + (g << 8) + (b << 0) + (alpha << 24);
		}		
		
		return palette;
	}

	
}
