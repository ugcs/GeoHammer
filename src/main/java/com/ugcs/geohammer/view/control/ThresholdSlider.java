package com.ugcs.geohammer.view.control;

import com.ugcs.geohammer.Settings;

import com.ugcs.geohammer.util.Unit;
import javafx.beans.value.ChangeListener;

public class ThresholdSlider  extends BaseSlider {
	
	public ThresholdSlider(Settings settings, ChangeListener<Number> listenerExt) {
		super(settings, listenerExt);
		name = "Threshold";
		unit = Unit.empty();
		tickUnits = 200;
	}

	public void updateUI() {
		slider.setMax(10000);
		slider.setMin(0);
		slider.setValue(settings.getThreshold());
	}
	
	public int updateModel() {
		settings.setThreshold((int)slider.getValue());
		return settings.getThreshold();
	}
}
