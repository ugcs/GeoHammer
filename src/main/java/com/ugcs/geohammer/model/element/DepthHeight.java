package com.ugcs.geohammer.model.element;

import java.awt.Shape;

import com.ugcs.geohammer.chart.gpr.ProfileField;
import com.ugcs.geohammer.model.TraceSample;
import com.ugcs.geohammer.chart.gpr.GPRChart;
import com.ugcs.geohammer.chart.ScrollableData;
import javafx.geometry.Point2D;

public class DepthHeight extends DepthStart {
	
	public DepthHeight(Shape shape) { //, GPRChart profField) {
		super(shape); //, profField);
	}

	@Override
	public void controlToSettings(TraceSample ts, ProfileField profField) {
		int max = profField.getMaxHeightInSamples();
		
		profField.getProfileSettings().hpage =
			Math.min(max - profField.getProfileSettings().getLayer(), Math.max(
				0, ts.getSample() - profField.getProfileSettings().getLayer()));
	}

	@Override
	protected Point2D getCenter(ScrollableData scrollable) {
		if (scrollable instanceof GPRChart gprChart) {
			var profField = gprChart.getField();
            int sample = profField.getProfileSettings().getLayer() + profField.getProfileSettings().hpage;
            int y = gprChart.sampleToScreen(sample);
            return new Point2D(gprChart.getField().getVisibleStart(), y);
		} else {
			return scrollable.traceSampleToScreen(0, 0);
		}
	}
}
