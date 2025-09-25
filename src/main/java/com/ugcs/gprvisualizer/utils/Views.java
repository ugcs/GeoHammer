package com.ugcs.gprvisualizer.utils;

import javafx.scene.paint.Color;

public final class Views {

    private Views() {
    }

    public static String toColorString(Color color) {
        Check.notNull(color);

        return String.format("#%02X%02X%02X",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
    }
}
