package com.ugcs.geohammer.view;

import com.ugcs.geohammer.Settings;

import javafx.beans.value.ChangeListener;

public class ZoomSlider extends BaseSlider {

	public ZoomSlider(Settings settings, ChangeListener<Number> listenerExt) {
		super(settings, listenerExt);
		name = "zoom";
		units = "%";
		tickUnits = 20;
	}

	@Override
	public int updateModel() {
		
		settings.zoom = (int) slider.getValue();
		return settings.zoom;
	}

	@Override
	public void updateUI() {
		slider.setMin(20);
		slider.setMax(500);
		slider.setValue(settings.zoom);
	}

}
