package com.ugcs.geohammer.map.layer.radar;

import com.ugcs.geohammer.model.Range;
import com.ugcs.geohammer.util.Unit;
import com.ugcs.geohammer.view.control.BaseSlider;

public class ThresholdSlider  extends BaseSlider {

	private final RadarSettings settings;

	public ThresholdSlider(RadarSettings settings) {
		super("Threshold", Unit.empty(), new Range(0, 20), 0.5);
		this.settings = settings;
		update();
	}

	@Override
	public void onValueChanged(Number value) {
		if (value != null) {
			settings.setThreshold(value.doubleValue());
		}
	}

	@Override
	public void update() {
		slider.setValue(settings.getThreshold());
	}
}
