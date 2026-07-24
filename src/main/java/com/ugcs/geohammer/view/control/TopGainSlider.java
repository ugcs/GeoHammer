package com.ugcs.geohammer.view.control;

import com.ugcs.geohammer.Settings;

import com.ugcs.geohammer.util.Unit;
import javafx.beans.value.ChangeListener;

public class TopGainSlider extends BaseSlider {

	public TopGainSlider(Settings settings, ChangeListener<Number> listenerExt) {
		super(settings, listenerExt);
		
		name = "Top gain";
		unit = Unit.symbol("%", "");
		tickUnits = 200;
	}

	@Override
	public int updateModel() {
		settings.setTopGain((int)slider.getValue());
		return settings.getTopGain();
	}

	@Override
	public void updateUI() {
		slider.setDisable(settings.isAutoGain());
		slider.setMin(1);
		slider.setMax(2000);
		slider.setValue(settings.getTopGain());
	}
}
