package com.ugcs.geohammer.map.layer.radar;

import com.ugcs.geohammer.model.Range;
import com.ugcs.geohammer.util.Unit;
import com.ugcs.geohammer.view.control.BaseSlider;

public class BottomGainSlider extends BaseSlider {

	private final RadarSettings settings;

	public BottomGainSlider(RadarSettings settings) {
		super("Bottom gain", Unit.symbol("%", ""), new Range(1, 100));
		this.settings = settings;
		update();
	}

	@Override
	public void onValueChanged(Number value) {
		if (value != null) {
			settings.setBottomGain(value.doubleValue());
		}
	}

	@Override
	public void update() {
		slider.setDisable(settings.isAutoGain());
		slider.setValue(settings.getBottomGain());
	}
}
