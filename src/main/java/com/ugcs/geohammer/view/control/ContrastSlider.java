package com.ugcs.geohammer.view.control;

import com.ugcs.geohammer.Settings;
import com.ugcs.geohammer.util.Unit;
import javafx.beans.value.ChangeListener;

public class ContrastSlider extends BaseSlider {

    public ContrastSlider(Settings settings, ChangeListener<Number> listenerExt) {
        super(settings, listenerExt);

        name = "Contrast";
        unit = Unit.empty();
        tickUnits = 25;
    }

    @Override
    public int updateModel() {
        settings.setContrast(slider.getValue());
        return (int) settings.getContrast();
    }

    @Override
    public void updateUI() {
        if (slider == null) {
            return;
        }
        slider.setMin(Settings.MIN_CONTRAST);
        slider.setMax(Settings.MAX_CONTRAST);
        slider.setValue(settings.getContrast());
    }
}
