package com.ugcs.geohammer.view;

import com.ugcs.geohammer.util.Check;
import javafx.scene.Parent;
import javafx.scene.Scene;

import java.net.URL;
import java.util.Objects;

public final class Styles {

    public static final String STATUS_STYLE_PATH = "/styles/status.css";

    public static final String DARK_THEME_PATH = "/styles/theme/dark.css";

    private Styles() {
    }

    public static String urlFromResource(String resourceName) {
        Check.notNull(resourceName);
        URL resourceUrl = Styles.class.getResource(resourceName);
        return Objects.requireNonNull(resourceUrl).toExternalForm();
    }

    public static void addResource(Parent target, String resourceName) {
        Check.notNull(target);
        target.getStylesheets().add(urlFromResource(resourceName));
    }

    public static void addResource(Scene scene, String resourceName) {
        Check.notNull(scene);
        scene.getStylesheets().add(urlFromResource(resourceName));
    }
}
