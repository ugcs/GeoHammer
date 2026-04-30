package com.ugcs.geohammer.view.style;

import com.ugcs.geohammer.PrefSettings;
import com.ugcs.geohammer.model.event.ThemeSelectedEvent;
import com.ugcs.geohammer.model.event.WhatChanged;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.view.Views;
import javafx.scene.Scene;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class ThemeService {

    private static final Theme DEFAULT_THEME = Theme.LUMA_LIGHT;

    private final ApplicationEventPublisher eventPublisher;

    private final PrefSettings preferences;

    private final Set<Scene> scenes = new HashSet<>();

    private Theme theme = DEFAULT_THEME;

    public ThemeService(ApplicationEventPublisher eventPublisher, PrefSettings preferences) {
        this.eventPublisher = eventPublisher;
        this.preferences = preferences;

        loadPreferences();
    }

    private void loadPreferences() {
        String themeTitle = preferences.getString("style", "theme");
        Theme theme = Theme.findByTitle(themeTitle);
        this.theme = theme != null ? theme : DEFAULT_THEME;
    }

    private void savePreferences() {
        String themeTitle = theme != null ? theme.title() : null;
        preferences.setValue("style", "theme", themeTitle);
    }

    public Theme getTheme() {
        return theme;
    }

    public void setTheme(Theme theme) {
        this.theme = Check.notNull(theme);
        savePreferences();

        Views.runNowOrLater(() -> {
            scenes.forEach(scene -> applyTheme(scene, theme));

            eventPublisher.publishEvent(new ThemeSelectedEvent(this, theme));
            eventPublisher.publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
        });
    }

    private void applyTheme(Scene scene, Theme theme) {
        Check.notNull(scene);
        Check.notNull(theme);

        List<String> urls = theme.stylesheets().stream()
                .map(Styles::urlFromResource)
                .toList();
        scene.getStylesheets().setAll(urls);
    }

    public void registerScene(Scene scene) {
        registerScene(scene, true);
    }

    public void registerScene(Scene scene, boolean trackChanges) {
        if (scene == null) {
            return;
        }
        Views.runNowOrLater(() -> {
            if (trackChanges) {
                if (!scenes.add(scene)) {
                    return;
                }
            }
            applyTheme(scene, theme);
        });
    }

    public void unregisterScene(Scene scene) {
        if (scene == null) {
            return;
        }
        Views.runNowOrLater(() -> {
            scenes.remove(scene);
        });
    }
}
