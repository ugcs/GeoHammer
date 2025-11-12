package com.ugcs.geohammer.view;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;

public abstract class BaseCheckBox {
	
	protected CheckBox checkBox;
	protected Label label;
	protected String name;
	protected ChangeListener<Boolean> listenerExt;
	protected Pos pos = Pos.CENTER_RIGHT;
//	public BaseCheckBox(Settings settings, String name){
//		this.settings = settings;
//		this.name = name;
//	}
	
	protected ChangeListener<Boolean> listener = new ChangeListener<Boolean>() {
        @Override
        public void changed(ObservableValue<? extends Boolean> source,
        		Boolean oldValue, Boolean newValue) {
        	boolean val = updateModel();
        	//label.textProperty().setValue(name + ": " + String.valueOf(val) + " " + units);
        } 
    };
	
	public BaseCheckBox(ChangeListener<Boolean> listenerExt, String name) {
		this.listenerExt = listenerExt;
		this.name = name;
	}
	
	public Node produce() {
		
		
		 
		checkBox = new CheckBox();
        
        updateUI();
        
        checkBox.selectedProperty().addListener(listener);
        checkBox.selectedProperty().addListener(listenerExt);
        //checkBox.setText(name);
        
        HBox root = new HBox();
        root.setAlignment(Pos.CENTER_RIGHT);
        root.setPadding(new Insets(5));
        root.setSpacing(5);        
        root.getChildren().addAll(new Text(name), checkBox);
        
        return root;
	}
	
	public abstract void updateUI();
	
	public abstract boolean updateModel();
	
}
