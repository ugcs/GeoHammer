package com.ugcs.geohammer.view.style;

import com.ugcs.geohammer.util.Strings;

import java.util.List;
import java.util.Objects;

public enum Theme {

    DEFAULT("Default", false, List.of(
            "/styles/theme/default.css",
            "/styles/theme/components.css"
    )),

    LUMA_LIGHT("Luma Light", false, List.of(
            "/styles/theme/luma.css",
            "/styles/theme/components.css"
    )),

    LUMA_DARK("Luma Dark", true, List.of(
            "/styles/theme/luma.css",
            "/styles/theme/luma-dark.css",
            "/styles/theme/components.css"
    ));

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
}
