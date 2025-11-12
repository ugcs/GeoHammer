package com.ugcs.geohammer.chart.gpr.axis;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import com.ugcs.geohammer.chart.gpr.GPRChart;
import com.ugcs.geohammer.util.Ticks;
import org.apache.commons.lang3.tuple.Pair;

import com.ugcs.geohammer.chart.gpr.axis.LeftRulerController.Converter;

public class VerticalRulerDrawer {
	
	private final GPRChart field;
	
	public VerticalRulerDrawer(GPRChart field) {
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
		int tick = Math.max(1, (int)Ticks.getPrettyTick(first, last, 10));

		List<Integer> steps = new ArrayList<>();
		steps.add(tick);
		if (tick % 2 == 0) {
			steps.add(tick /2);
		}
		if (tick % 10 == 0) {
			steps.add(tick / 10);
		}

		g2.setFont(new Font("Arial", Font.PLAIN, 11));

		int sz = 21;
		for (int step : steps) {
			int s = (int)Math.ceil((double)first / step) * step;
			int f = last / step * step;
			for (int i = s; i <= f; i += step) {
				int y = field.sampleToScreen(converter.back(i));

				g2.setColor(Color.lightGray);
				g2.drawLine(rect.x, y, rect.x + sz, y);

				if (step == tick) {
					g2.setColor(Color.darkGray);
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
