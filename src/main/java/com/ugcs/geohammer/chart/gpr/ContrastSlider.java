package com.ugcs.geohammer.chart.gpr;

import com.ugcs.geohammer.model.Range;
import com.ugcs.geohammer.util.Unit;
import com.ugcs.geohammer.view.control.BaseSlider;

public class ContrastSlider extends BaseSlider {

    private final ProfileSettings profileSettings;

    public ContrastSlider(ProfileSettings profileSettings) {
        super("Contrast", Unit.empty(), new Range(ProfileSettings.MIN_CONTRAST, ProfileSettings.MAX_CONTRAST), 25);
        this.profileSettings = profileSettings;
        update();
    }

    @Override
    public void onValueChanged(Number value) {
        if (value != null) {
            profileSettings.setContrast(value.doubleValue());
        }
    }

    @Override
    public void update() {
        slider.setValue(profileSettings.getContrast());
    }
}
