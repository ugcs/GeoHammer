package com.ugcs.geohammer.view.control;

import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

public abstract class BaseCheckBox {
	
	protected CheckBox checkBox;

	protected Label label;

	protected String name;

	protected ChangeListener<Boolean> listenerExt;

	protected Pos pos = Pos.CENTER_RIGHT;

	protected ChangeListener<Boolean> listener = (source, oldValue, newValue) -> updateModel();
	
	public BaseCheckBox(ChangeListener<Boolean> listenerExt, String name) {
		this.listenerExt = listenerExt;
		this.name = name;
	}
	
	public Node produce() {
		checkBox = new CheckBox();
        
        updateUI();
        
        checkBox.selectedProperty().addListener(listener);
        checkBox.selectedProperty().addListener(listenerExt);

        HBox root = new HBox();
        root.setAlignment(Pos.CENTER_RIGHT);
        root.setPadding(new Insets(5));
        root.setSpacing(5);        
        root.getChildren().addAll(new Label(name), checkBox);
        
        return root;
	}
	
	public abstract void updateUI();
	
	public abstract void updateModel();
}
