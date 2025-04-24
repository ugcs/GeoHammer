package com.ugcs.gprvisualizer.ui;

import com.ugcs.gprvisualizer.gpr.Settings;

import javafx.beans.value.ChangeListener;

public class ThresholdSlider  extends BaseSlider {
	
	public ThresholdSlider(Settings settings, ChangeListener<Number> listenerExt) {
		super(settings, listenerExt);
		name = "Threshold";
		units = "";
		tickUnits = 200;
	}

	public void updateUI() {
		//slider.setDisable(settings.autogain);
		slider.setMax(10000);		
		slider.setMin(0);
		slider.setValue(settings.threshold);
	}
	
	public int updateModel() {
		settings.threshold = (int) slider.getValue();
		return settings.threshold;
	}
}
