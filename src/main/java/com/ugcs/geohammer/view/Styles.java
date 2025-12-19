package com.ugcs.geohammer.view;

import javafx.scene.Parent;

import java.net.URL;
import java.util.Objects;

public final class Styles {

    public static final String STATUS_STYLE_PATH = "/styles/status.css";

    private Styles() {
    }

    public static String urlFromResource(String resourceName) {
        URL resourceUrl = Styles.class.getResource(resourceName);
        return Objects.requireNonNull(resourceUrl).toExternalForm();
    }

    public static void addResource(Parent target, String resourceName) {
        target.getStylesheets().add(urlFromResource(resourceName));
    }
}
