package com.ugcs.gprvisualizer.ui;

import org.apache.commons.lang3.mutable.MutableInt;

import javafx.beans.value.ChangeListener;
import javafx.scene.Node;

public class SliderFactory {

	public static Node create(String name, MutableInt valueHolder, 
			int min, int max, ChangeListener<Number> listener,
			double tickUnits) {

		BaseSlider sliderProducer = new BaseSlider(null, listener) {

			public void updateUI() {
				slider.setMax(max);
				slider.setMin(min);
				slider.setValue(valueHolder.intValue());
			}

			public int updateModel() {
				valueHolder.setValue((int) slider.getValue());
				return valueHolder.intValue();
			}
		};

		sliderProducer.name = name;
		sliderProducer.units = "";
		sliderProducer.tickUnits = tickUnits;

		return sliderProducer.produce();
	}

}
