package com.ugcs.gprvisualizer.ui;

import com.ugcs.gprvisualizer.gpr.Settings;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

public abstract class BaseSlider {

	protected Settings settings;
	protected Slider slider;
	protected String name;
	protected String units = "";
	protected Label label;
	protected double tickUnits = 25;
	protected ChangeListener<Number> listenerExt;
	protected ChangeListener<Number> listener = new ChangeListener<Number>() {
        @Override
        public void changed(ObservableValue<? extends Number> source,
        		Number oldValue, Number newValue) {
        	int val = updateModel();
        	label.textProperty().setValue(name + ": " + String.valueOf(val) + " " + units);
        } 
    };
	
	public BaseSlider(Settings settings, ChangeListener<Number> listenerExt) {
		this.settings = settings;
		this.listenerExt = listenerExt;
	}
	
	public Node produce() {
        slider = new Slider();
        
        updateUI();
        
        slider.setPrefWidth(200);
        
        slider.valueProperty().addListener(listener);
        slider.valueProperty().addListener(listenerExt);
        
        slider.setShowTickLabels(true);
        slider.setShowTickMarks(true);
        slider.setMajorTickUnit(tickUnits);
        slider.setBlockIncrement(1);

        label = new Label(name);
        
        listener.changed(null, null, null);
        
        HBox root = new HBox();
        root.setPadding(new Insets(4, 8, 4, 0));
        root.setSpacing(2.5);
        root.setPrefWidth(Double.MAX_VALUE);

        Region spacer = new Region();
        spacer.setMinWidth(0);
        HBox.setHgrow(spacer, Priority.ALWAYS);

        root.getChildren().addAll(label, spacer, slider);
        
        updateUI();
        return root;
	}
	
	public abstract int updateModel();
	
	public abstract void updateUI();
	
}
