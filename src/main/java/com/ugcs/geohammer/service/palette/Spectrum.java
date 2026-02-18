package com.ugcs.geohammer.service.palette;

import java.awt.Color;

public interface Spectrum {

    Color getColor(double value);

    static Color lerp(Color a, Color b, double t) {
        return new Color(
                lerp(a.getRed(), b.getRed(), t),
                lerp(a.getGreen(), b.getGreen(), t),
                lerp(a.getBlue(), b.getBlue(), t)
        );
    }

    static int lerp(int a, int b, double t) {
        return (int)(a + t * (b - a));
    }
}
