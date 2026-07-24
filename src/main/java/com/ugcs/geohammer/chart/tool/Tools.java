package com.ugcs.geohammer.chart.tool;

import com.ugcs.geohammer.view.Views;
import javafx.scene.Node;
import javafx.scene.layout.VBox;

public final class Tools {

    private Tools() {
    }

    public static VBox createToolContainer(Node... children) {
        VBox container = new VBox(Views.DEFAULT_SPACING, children);
        container.setPadding(Views.DEFAULT_OPTIONS_INSETS);
        return container;
    }
}
