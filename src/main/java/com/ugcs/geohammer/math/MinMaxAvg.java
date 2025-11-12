package com.ugcs.geohammer.math;

public class MinMaxAvg {

	private double sum = 0;
	private double cnt = 0;
	
	private double min;
	private double max;
	private boolean first  = true;
	
	public void put(double x) {
		if (first) {
			first = false;
			min = x;
			max = x;
		}
		
		min = Math.min(min, x);
		max = Math.max(max, x);
		
		sum += x;
		cnt++;
	}

	public double getAvg() {
		if (cnt > 0) {
			return sum / cnt;
		} else {
			return 0;
		}
	}
	
	public double getMax() {
		return max;
	}

	public double getMin() {
		return min;
	}

	public double getMid() {
		return (min + max) / 2;
	}
	
	public boolean isNotEmpty() {
		return !first; 
	}
}
