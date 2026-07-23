package com.ugcs.geohammer.view.control;

import com.ugcs.geohammer.Settings;

import javafx.beans.value.ChangeListener;

public class BottomGainSlider extends BaseSlider {

	public BottomGainSlider(Settings settings, ChangeListener<Number> listenerExt) {
		super(settings, listenerExt);
		name = "Bottom gain";
		units = "%";
		tickUnits = 200;
	}

	@Override
	public int updateModel() {
		settings.setBottomGain((int) slider.getValue());
		return settings.getBottomGain();
	}

	@Override
	public void updateUI() {
		slider.setDisable(settings.isAutoGain());
		slider.setMin(1);
		slider.setMax(2000);
		slider.setValue(settings.getBottomGain());
	}
}
