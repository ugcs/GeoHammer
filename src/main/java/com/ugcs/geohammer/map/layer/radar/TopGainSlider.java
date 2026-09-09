package com.ugcs.geohammer.map.layer.radar;

import com.ugcs.geohammer.model.Range;
import com.ugcs.geohammer.util.Unit;
import com.ugcs.geohammer.view.control.BaseSlider;

public class TopGainSlider extends BaseSlider {

	private final RadarSettings settings;

	public TopGainSlider(RadarSettings settings) {
		super("Top gain", Unit.symbol("%", ""), new Range(1, 100));
		this.settings = settings;
		update();
	}

	@Override
	public void onValueChanged(Number value) {
		if (value != null) {
			settings.setTopGain(value.doubleValue());
		}
	}

	@Override
	public void update() {
		slider.setValue(settings.getTopGain());
		slider.setDisable(settings.isAutoGain());
	}
}
