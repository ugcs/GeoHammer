package com.ugcs.geohammer.chart.tool;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
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

    static ScrollPane createVerticalScrollContainer(Node content, Node parent) {
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        // set reasonably large amount to fit tab height;
        // this seems the only way to force pane to fill container
        // in height
        scrollPane.setPrefHeight(10_000);
        if (parent != null) {
            scrollPane.focusedProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue) {
                    // redirect focus to the parent node
                    Platform.runLater(parent::requestFocus);
                }
            });
        }
        return scrollPane;
    }
}
