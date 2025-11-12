package com.ugcs.geohammer.view;

import com.ugcs.geohammer.Settings;

import javafx.beans.value.ChangeListener;

public class DepthSlider extends BaseSlider {
	
	public DepthSlider(Settings settings, ChangeListener<Number> listenerExt) {
		super(settings, listenerExt);
		name = "depth";
		units = "samples";
		tickUnits = 100;
	}

	public void updateUI() {
		slider.setMax(settings.maxsamples);		
		slider.setMin(0);
		slider.setValue(settings.getLayer());
	}
	
	public int updateModel() {
		settings.setLayer((int) slider.getValue());
		return settings.getLayer();
	}
}
