package com.ugcs.gprvisualizer.ui;

import com.ugcs.gprvisualizer.gpr.Settings;

import javafx.beans.value.ChangeListener;

public class GainTopSlider extends BaseSlider {

	public GainTopSlider(Settings settings, ChangeListener<Number> listenerExt) {
		super(settings, listenerExt);
		
		name = "Top gain";
		units = "%";
		tickUnits = 200;
	}

	@Override
	public int updateModel() {
		
		settings.topscale = (int) slider.getValue();
		return settings.topscale;
	}

	@Override
	public void updateUI() {
		
		slider.setDisable(settings.autogain);
		slider.setMin(1);
		slider.setMax(2000);
		slider.setValue(settings.topscale);
		
	}

}
