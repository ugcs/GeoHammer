package com.ugcs.geohammer.math;

public class HoughDiscretizer {

	public static final int DISCRET_SIZE = 21;
	public static final double DISCRET_GOOD_FROM = 5;
	
	public static final double FACTORX_FROM = 0.519;	
	public static final double FACTORX_TO = 1.5;
	public static final double FACTORX_WIDTH = FACTORX_TO - FACTORX_FROM;
	
	public static final double D = FACTORX_TO / FACTORX_FROM;
	public static final double STEP = Math.pow(D, 1.0 / (double) DISCRET_SIZE);
	
	public static final int OUTSIDE_STEPS = 7;
	public static final int INSIDE_STEPS = 8;
	
	public int transform(double factorX) {
		
		if (factorX < FACTORX_FROM) {
			return 0;
		}
		
//		double norm = (factorX - FACTORX_FROM) 
//				/ (FACTORX_TO - FACTORX_FROM);
//
//		if (norm > 1) {
//			return DISCRET_SIZE;
//		}
//		
//		int res = (int) Math.round(norm * (DISCRET_SIZE - 1));
		
		
		
		double z = Math.log(factorX / FACTORX_FROM) / Math.log(STEP);
		
		int res = (int) Math.round(z);
		
		
		return res;// > 1 ? res : 0;
		
	}
	
	public double back(int discr) {
		
		return FACTORX_FROM * Math.pow(STEP, discr);
	}
	
	
	public static void main(String[] args) {
		HoughDiscretizer d = new HoughDiscretizer();
		
		System.out.println(" step " + STEP);
		
		for(int i = 0; i < 22; i+= 1) {
			
			double i2 = d.back(i);
			int z = d.transform(i2);			
			
			System.out.println(i + " -> " + i2 + " -> " + z);
		}
		
	}
	
	
}
