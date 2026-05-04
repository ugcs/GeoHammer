package com.ugcs.geohammer.view;

import com.ugcs.geohammer.util.Check;
import javafx.scene.paint.Color;

public final class Colors {

    public static java.awt.Color AWT_TRANSPARENT = new java.awt.Color(0, 0, 0, 0);

    private Colors() {
    }

    public static java.awt.Color awtColor(Color color) {
        return new java.awt.Color(
                (float) color.getRed(),
                (float) color.getGreen(),
                (float) color.getBlue(),
                (float) color.getOpacity()
        );
    }

    public static java.awt.Color opaque(java.awt.Color color, float opacity) {
        int alpha = (int)(opacity * color.getAlpha());
        int rgba = (color.getRGB() & 0xffffff) | (alpha << 24);
        return new java.awt.Color(rgba, true);
    }

    public static Color opaque(Color color, float opacity) {
        return color.deriveColor(0, 1, 1, opacity);
    }

    public static Color fxColor(java.awt.Color color) {
        return Color.rgb(
                color.getRed(),
                color.getGreen(),
                color.getBlue(),
                color.getAlpha() / 255.0);
    }

    public static String toColorString(Color color) {
        Check.notNull(color);

        return String.format("#%02X%02X%02X",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
    }
}
