package com.ugcs.geohammer.model.event;

import com.ugcs.geohammer.view.style.Theme;

public class ThemeSelectedEvent extends BaseEvent {

    private final Theme theme;

    public ThemeSelectedEvent(Object source, Theme theme) {
        super(source);

        this.theme = theme;
    }

    public Theme getTheme() {
        return theme;
    }
}
