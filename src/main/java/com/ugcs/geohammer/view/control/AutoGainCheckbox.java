package com.ugcs.geohammer.view.control;

import com.ugcs.geohammer.Settings;

import javafx.beans.value.ChangeListener;

public class AutoGainCheckbox extends BaseCheckBox {

	protected Settings settings;
	
	public AutoGainCheckbox(Settings settings, ChangeListener<Boolean> listenerExt) {
		super(listenerExt, "Autogain");
		this.settings = settings;
	}

	@Override
	public void updateUI() {
		checkBox.setSelected(settings.isAutoGain());
	}

	@Override
	public void updateModel() {
		settings.setAutoGain(checkBox.isSelected());
	}
}
