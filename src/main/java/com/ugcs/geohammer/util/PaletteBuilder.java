package com.ugcs.geohammer.util;

public class PaletteBuilder {

	private double scale = 25.0;

	private int start = 0;
	
	public PaletteBuilder() {
	}

	public PaletteBuilder(double scale, int start) {
		this.scale = scale;
		this.start = start;
	}
	
	public int[] build() {
		int[] palette = new int[15000];
		for (int i = 0; i < palette.length; i++) {
			double t = ((double) i + start) / scale;
			
			int r = ((int) ((Math.cos(t * 1.50) + 1) / 2 * 255.0)) & 0xff;
			int g = ((int) ((Math.cos(t * 1.23) + 1) / 2 * 255.0)) & 0xff;
			int b = ((int) ((Math.cos(t * 1.00) + 1) / 2 * 255.0)) & 0xff;
			int alpha = (int) (i < 55.0 ? i / 55.0 * 180.0 : 180.0);
			
			palette[i] = r + (g << 8) + (b << 16) + (alpha << 24);
		}

		return palette;
	}
}
