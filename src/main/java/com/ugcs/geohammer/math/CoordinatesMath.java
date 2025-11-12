package com.ugcs.geohammer.math;

public class CoordinatesMath {

	// Radius of earth in KM
	private static final double  R = 6378.137; 

	// generally used geo measurement function
	// return value in meters
	public static double measure(double lat1, double lon1, 
			double lat2, double lon2) {
		double deltaLat = toRad(lat2) - toRad(lat1);
		double deltaLon = toRad(lon2) - toRad(lon1);
		
		double  a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) 
				+ Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) 
				* Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
		
		double  c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		double  d = R * c;
	    return d * 1000; // meters
	}	

	
	private static double toRad(double degree) {
		return degree * Math.PI / 180;
	}
	
	// generally used geo measurement function
	private double measureHor(double lat1, double lon1, double lon2) {
		// lon - долгота - горизонталь
		// lat - шиорта - вертикаль
		
		double  deltaLon = toRad(lon2) - toRad(lon1);
		
		double  a = 
			Math.cos(toRad(lat1)) * Math.cos(toRad(lat1)) 
			* Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
		
		double  c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		double  d = R * c;
	    return d * 1000; // meters
	}	

}
