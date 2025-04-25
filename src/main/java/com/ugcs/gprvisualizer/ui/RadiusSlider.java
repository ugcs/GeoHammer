package com.ugcs.gprvisualizer.ui;

import com.ugcs.gprvisualizer.gpr.Settings;

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
		
		settings.radius = (int) slider.getValue();
		return settings.radius;
	}

	@Override
	public void updateUI() {
		slider.setMin(2);
		slider.setMax(50);
		slider.setValue(settings.radius);
	}
}
