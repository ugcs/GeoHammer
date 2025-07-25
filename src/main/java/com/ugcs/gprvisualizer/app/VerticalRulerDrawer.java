package com.ugcs.gprvisualizer.app;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;

import org.apache.commons.lang3.tuple.Pair;

import com.ugcs.gprvisualizer.gpr.LeftRulerController.Converter;

public class VerticalRulerDrawer {
	
	private final GPRChart field;
	
	VerticalRulerDrawer(GPRChart field) {
		this.field = field;
	}
	
	public void draw(Graphics2D g2) {

		Rectangle rect = field.getField().getLeftRuleRect();
		int firstSample;		
		int lastSample; 
		
		firstSample = field.getStartSample();		
		lastSample = 
				Math.min(
					field.getLastVisibleSample(),
					field.getField().getMaxHeightInSamples());
		
		Converter converter = getConverter();
		Pair<Integer, Integer> p = converter.convert(firstSample, lastSample);
		int first = p.getLeft();
		int last = p.getRight();
		
		int[] tick = {100, 50, 10, 5};
		
		int sz = 21;
		
		for (int b : tick) {
			
			int s = (first / b + 1) * b;
			int f = last / b * b;
			boolean drawText = (f - s) / b < 8;
			
			for (int i = s; i <= f; i += b) {
				
				int y = field.sampleToScreen(converter.back(i));
				
				g2.setColor(Color.lightGray);
                g2.drawLine(rect.x , y, rect.x + sz, y);

                if (drawText) {
					g2.setColor(Color.darkGray);
					g2.setFont(new Font("Arial", Font.PLAIN, 12));
					g2.drawString(String.format("%1$3s", i), 
							rect.x + rect.width / 3, y + 4);
				}
				
			}
			sz = sz * 2 / 3;
		}		
	}

	private Converter getConverter() {
		return field.getLeftRulerController().getConverter();
	}
}
