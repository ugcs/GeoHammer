package com.ugcs.geohammer.view.control;

import com.ugcs.geohammer.Settings;

import javafx.beans.value.ChangeListener;

public class RadiusSlider extends BaseSlider {

	public RadiusSlider(Settings settings, ChangeListener<Number> listenerExt) {
		super(settings, listenerExt);
		name = "Radius";
		units = "px";
		tickUnits = 10;
	}

	@Override
	public int updateModel() {
		settings.setRadius((int)slider.getValue());
		return settings.getRadius();
	}

	@Override
	public void updateUI() {
		slider.setMin(2);
		slider.setMax(50);
		slider.setValue(settings.getRadius());
	}
}
