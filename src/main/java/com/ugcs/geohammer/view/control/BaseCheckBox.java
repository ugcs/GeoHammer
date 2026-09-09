package com.ugcs.geohammer.view.control;

import com.ugcs.geohammer.view.Listeners;
import com.ugcs.geohammer.view.Views;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

public abstract class BaseCheckBox extends HBox {
	
	protected final CheckBox checkBox;

	protected final Label label;

	protected final String name;

	public BaseCheckBox(String name) {
		this.name = name;

		checkBox = new CheckBox();
		label = new Label(name);

		Listeners.onChange(checkBox.selectedProperty(), this::onValueChanged);

		setSpacing(Views.DEFAULT_SPACING);
		setPadding(new Insets(Views.DEFAULT_SPACING));
		setAlignment(Pos.CENTER_RIGHT);
		getChildren().addAll(label, checkBox);
	}

	public CheckBox getCheckBox() {
		return checkBox;
	}

	public Label getLabel() {
		return label;
	}

	public abstract void onValueChanged(Boolean value);
	
	public abstract void update();
}
