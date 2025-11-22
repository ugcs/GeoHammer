package com.ugcs.geohammer.chart.tool;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jspecify.annotations.NonNull;

public final class Tools {

    public static final double DEFAULT_SPACING = 5;
    public static final Insets DEFAULT_OPTIONS_INSETS = new Insets(10, 0, 10, 0);

    private Tools() {
    }

    public static ToggleButton createToggle(String text, StackPane targetView) {
        ToggleButton toggle = new ToggleButton(text);
        toggle.setMaxWidth(Double.MAX_VALUE);
        toggle.setOnAction(Tools.getChangeVisibleAction(targetView));
        return toggle;
    }

    static ScrollPane createVerticalScrollContainer(Node content) {
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        // set reasonably large amount to fit tab height;
        // this seems the only way to force pane to fill container
        // in height
        scrollPane.setPrefHeight(10_000);
        return scrollPane;
    }

    static @NonNull EventHandler<ActionEvent> getChangeVisibleAction(StackPane filterOptionsStackPane) {
        return e -> {
            filterOptionsStackPane.getChildren()
                    .stream().filter(n -> n instanceof VBox).forEach(options -> {
                        boolean visible = options.isVisible();
                        options.setVisible(!visible);
                        options.setManaged(!visible);
                    });
        };
    }
}
