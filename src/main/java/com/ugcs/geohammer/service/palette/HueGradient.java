package com.ugcs.geohammer.service.palette;

import java.awt.Color;

public class HueGradient implements Spectrum {

    private static final float MAX_HUE = 0.84f;

    private static final float SATURATION = 0.80f;

    private static final float BRIGHTNESS = 0.90f;

    @Override
    public Color getColor(double value) {
        double normalized = Math.clamp(value, 0, 1);
        return Color.getHSBColor(
                (float)(1 - normalized) * MAX_HUE,
                SATURATION,
                BRIGHTNESS);
    }
}
