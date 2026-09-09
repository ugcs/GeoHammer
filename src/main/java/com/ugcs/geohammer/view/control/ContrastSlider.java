package com.ugcs.geohammer.view.control;

import com.ugcs.geohammer.Settings;
import com.ugcs.geohammer.model.Range;
import com.ugcs.geohammer.util.Unit;

public class ContrastSlider extends BaseSlider {

    private final Settings settings;

    public ContrastSlider(Settings settings) {
        super("Contrast", Unit.empty(), new Range(Settings.MIN_CONTRAST, Settings.MAX_CONTRAST));
        this.settings = settings;
        update();
    }

    @Override
    public void onValueChanged(Number value) {
        if (value != null) {
            settings.setContrast(value.doubleValue());
        }
    }

    @Override
    public void update() {
        slider.setValue(settings.getContrast());
    }
}
