package com.ugcs.geohammer.model.element;

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;

public class ShapeHolder {

	public static Shape flag = getFlag();
	public static Shape flag2 = getFlag2();
	public static Shape flag3 = getFlag3();
	public static Shape topSelection = createTop(); 
	public static Shape botSelection = createBot();
	
	static Shape getFlag() {
		GeneralPath result = new GeneralPath(Path2D.WIND_NON_ZERO, 5);
	    double step = 5;
	    
	    result.moveTo(0, 0);
	    
	    result.lineTo(0, -5 * step);
	    result.lineTo(3 * step, -5 * step);
	    result.lineTo(2 * step, -4 * step);
	    result.lineTo(3 * step, -3 * step);
	    result.lineTo(2, -3 * step);
	    result.lineTo(2, 0);
	    
	    result.closePath();
	    return result;
	}

	static Shape getFlag2() {
		GeneralPath result = new GeneralPath(Path2D.WIND_NON_ZERO, 5);
		double step = 7;

		result.moveTo(0, 0);

		result.lineTo(0, -5 * step);
		result.lineTo(3 * step, -5 * step);
		//result.lineTo(2 * step, -4 * step);
		result.lineTo(3 * step, -3 * step);
		result.lineTo(2, -3 * step);
		result.lineTo(0, 0);

		result.closePath();
		return result;
	}

	static Shape getFlag3() {
		GeneralPath result = new GeneralPath(Path2D.WIND_NON_ZERO, 5);
		double step = 7;

		result.moveTo(0, 0);

		result.lineTo(-2 * step, -5 * step);
		result.lineTo(2 * step, -5 * step);
		result.lineTo(0, 0);

		result.closePath();
		return result;
	}

	static Shape createTop() {
		GeneralPath result = new GeneralPath(Path2D.WIND_NON_ZERO, 5);
	    double step = 5;
	    
	    result.moveTo(0, 0);
	    
	    result.lineTo(0, -3 * step);
	    result.lineTo(3 * step, 0);
	    
	    result.closePath();
	    
	    Rectangle r = result.getBounds();
	    
	    
	    return result;
	}

	static Shape createBot() {
		GeneralPath result = new GeneralPath(Path2D.WIND_NON_ZERO, 5);
	    double step = 5;
	    
	    result.moveTo(0, 0);
	    
	    result.lineTo(0, 3 * step);	    
	    result.lineTo(3 * step, 0);	    
	    
	    result.closePath();
	    
	    return result;
	}
	
}
