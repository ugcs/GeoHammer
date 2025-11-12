package com.ugcs.geohammer.chart.gpr;

public class Tanh {
	
	private static final int HALF = 20000;
	
	float thr;
	int[] calc = new int[HALF * 2];
	
	public void setThreshold(float thr) {
		if (this.thr != thr) {
			this.thr = thr;
			
			for (int i = 0; i < calc.length; i++) {
				int val = i - HALF;
				int c = (int) (127.0 - Math.tanh(val / thr) * 127.0);
				int color = ((c) << 16) + ((c) << 8) + c;
				
				calc[i] = color;
			}
		}		
	}
	
	public int trans(float value) {
		
		int i = (int) (value + HALF);
		i = Math.min(i, HALF * 2 - 1);
		i = Math.max(i, 0);
		return calc[i];
	}
}
