package com.ugcs.geohammer.model.element;

import java.awt.Shape;

import com.ugcs.geohammer.chart.gpr.ProfileField;
import com.ugcs.geohammer.chart.gpr.GPRChart;
import com.ugcs.geohammer.chart.ScrollableData;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.model.TraceSample;
import javafx.geometry.Point2D;
import javafx.scene.control.Tooltip;

public class DepthHeight extends DepthStart {

	public DepthHeight(Shape shape, Tooltip tooltip) {
		super(shape, tooltip);
	}

	@Override
	protected IndexRange buildIndexRange(TraceSample ts, ProfileField profField) {
		var settings = profField.getProfileSettings();
		return new IndexRange(settings.getLayer(), ts.getSample());
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
