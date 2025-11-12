package com.ugcs.geohammer.view;

import javafx.beans.value.ChangeListener;
import javafx.geometry.Pos;

public class LayerVisibilityCheckbox extends BaseCheckBox {

	public LayerVisibilityCheckbox(String name, ChangeListener<Boolean> listenerExt) {
		super(listenerExt, name);
		
		pos = Pos.CENTER_LEFT;
	}

	@Override
	public void updateUI() {
		
		checkBox.setSelected(true);
	}

	@Override
	public boolean updateModel() {
		
		return false;
	}

}
