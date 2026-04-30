package com.ugcs.geohammer.view.style;

import javafx.scene.paint.Color;

import java.util.List;
import java.util.Objects;

public enum Theme {

    LUMA_LIGHT("Luma Light", false, List.of(
            "/styles/theme/luma.css",
            "/styles/theme/components.css"
    )),

    LUMA_DARK("Luma Dark", true, List.of(
            "/styles/theme/luma.css",
            "/styles/theme/luma-dark.css",
            "/styles/theme/components.css"
    )),

    BASIC("Basic", false, List.of(
            "/styles/theme/basic.css",
            "/styles/theme/components.css"
    ));

    private static final Color STROKE_COLOR_LIGHT = Color.gray(0.2);

    private static final Color STROKE_COLOR_DARK = Color.gray(0.8);

    private final String title;

    private final boolean dark;

    private final List<String> stylesheets;

    Theme(String title, boolean dark, List<String> stylesheets) {
        this.title = title;
        this.dark = dark;
        this.stylesheets = stylesheets;
    }

    public static Theme findByTitle(String title) {
        for (Theme theme : values()) {
            if (Objects.equals(title, theme.title)) {
                return theme;
            }
        }
        return null;
    }

    public String title() {
        return title;
    }

    public boolean dark() {
        return dark;
    }

    public List<String> stylesheets() {
        return stylesheets;
    }

    public Color strokeColor() {
        return dark ? STROKE_COLOR_DARK : STROKE_COLOR_LIGHT;
    }
}
