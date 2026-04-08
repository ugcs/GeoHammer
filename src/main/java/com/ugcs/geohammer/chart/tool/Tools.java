package com.ugcs.geohammer.chart.tool;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.VBox;

public final class Tools {

    public static final double DEFAULT_SPACING = 5;

    public static final Insets DEFAULT_OPTIONS_INSETS = new Insets(10, 0, 5, 0);

    private Tools() {
    }

    public static VBox createToolContainer(Node... children) {
        VBox container = new VBox(Tools.DEFAULT_SPACING, children);
        container.setPadding(Tools.DEFAULT_OPTIONS_INSETS);
        return container;
    }
}
