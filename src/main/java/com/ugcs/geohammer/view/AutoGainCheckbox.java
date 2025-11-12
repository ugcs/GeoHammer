package com.ugcs.geohammer.view;

import com.ugcs.geohammer.Settings;

import javafx.beans.value.ChangeListener;

public class AutoGainCheckbox extends BaseCheckBox {
	protected Settings settings;
	
	public AutoGainCheckbox(Settings settings, ChangeListener<Boolean> listenerExt) {
		super(listenerExt, "Autogain");
		this.settings = settings;
	}

	public void updateUI() {
		checkBox.setSelected(settings.autogain);
	}
	
	public boolean updateModel() {
		settings.autogain = checkBox.isSelected();
		return settings.autogain;
	}
}
