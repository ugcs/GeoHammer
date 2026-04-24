package com.ugcs.geohammer.view.style;

import com.ugcs.geohammer.util.Check;
import javafx.collections.ObservableList;
import javafx.scene.Parent;
import javafx.scene.Scene;

import java.net.URL;

public final class Styles {

    private Styles() {
    }

    public static String urlFromResource(String resourcePath) {
        Check.notEmpty(resourcePath);
        URL resourceUrl = Styles.class.getResource(resourcePath);
        return Check.notNull(resourceUrl).toExternalForm();
    }

    private static void addStyle(ObservableList<String> stylesheets, String resourcePath) {
        Check.notNull(stylesheets);
        Check.notEmpty(resourcePath);
        stylesheets.add(urlFromResource(resourcePath));
    }

    public static void addStyle(Parent node, String resourcePath) {
        Check.notNull(node);
        addStyle(node.getStylesheets(), resourcePath);
    }

    public static void addStyle(Scene scene, String resourcePath) {
        Check.notNull(scene);
        addStyle(scene.getStylesheets(), resourcePath);
    }
}
