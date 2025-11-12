package com.ugcs.geohammer.view;

import com.ugcs.geohammer.Settings;

import javafx.beans.value.ChangeListener;

public class GainBottomSlider extends BaseSlider {

	public GainBottomSlider(Settings settings, ChangeListener<Number> listenerExt) {
		super(settings, listenerExt);
		name = "Bottom gain";
		units = "%";
		tickUnits = 200;
	}

	@Override
	public int updateModel() {
		
		settings.bottomscale = (int) slider.getValue();
		return settings.bottomscale;
	}

	@Override
	public void updateUI() {
		slider.setDisable(settings.autogain);
		slider.setMin(1);
		slider.setMax(2000);
		slider.setValue(settings.bottomscale);
	}

}
