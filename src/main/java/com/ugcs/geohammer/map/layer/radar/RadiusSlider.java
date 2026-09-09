package com.ugcs.geohammer.map.layer.radar;

import com.ugcs.geohammer.model.Range;
import com.ugcs.geohammer.util.Unit;
import com.ugcs.geohammer.view.control.BaseSlider;

public class RadiusSlider extends BaseSlider {

	private final RadarSettings settings;

	public RadiusSlider(RadarSettings settings) {
		super("Radius", Unit.symbol("px"), new Range(2.5, 50), 2.5);
		this.settings = settings;
		update();
	}

	@Override
	public void onValueChanged(Number value) {
		if (value != null) {
			settings.setRadius(value.intValue());
		}
	}

	@Override
	public void update() {
		slider.setValue(settings.getRadius());
	}
}
