package com.ugcs.geohammer.map.layer.radar;

import com.ugcs.geohammer.view.control.BaseCheckBox;

public class AutoGainCheckBox extends BaseCheckBox {

	private final RadarSettings settings;
	
	public AutoGainCheckBox(RadarSettings settings) {
		super("Autogain");
		this.settings = settings;

		update();
	}

	@Override
	public void onValueChanged(Boolean value) {
		if (value != null) {
			settings.setAutoGain(value);
		}
	}

	@Override
	public void update() {
		checkBox.setSelected(settings.isAutoGain());
	}
}
