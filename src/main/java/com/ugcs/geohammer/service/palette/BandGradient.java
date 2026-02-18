package com.ugcs.geohammer.service.palette;

import com.ugcs.geohammer.util.Check;

import java.awt.Color;

public class BandGradient implements Spectrum {

    private final Color[] bands;

    public BandGradient(Color[] bands) {
        Check.notNull(bands);
        Check.condition(bands.length > 1);
        this.bands = bands;
    }

    @Override
    public Color getColor(double value) {
        double scaled = value * (bands.length - 1);
        int i = (int)Math.floor(scaled); // lower band
        if (i == bands.length - 1) {
            i--;
        }
        return Spectrum.lerp(bands[i], bands[i + 1], scaled - i);
    }
}
