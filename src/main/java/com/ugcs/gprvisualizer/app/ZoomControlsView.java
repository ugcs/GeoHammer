package com.ugcs.gprvisualizer.app;

import com.github.thecoldwine.sigrun.common.ext.MapField;
import com.ugcs.gprvisualizer.event.WhatChanged;
import com.ugcs.gprvisualizer.gpr.Model;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Layer providing a zoom buttons control, displayed at the bottom of the map view.
 * It does not draw anything onto the Graphics2D surface.
 */
@Component
public class ZoomControlsView {

    private final VBox container = new VBox();

    @Autowired
    private Model model;

    @Autowired
    private ApplicationEventPublisher publisher;

    private final Button zoomInButton = new Button();
    private final Button zoomOutButton = new Button();

    public ZoomControlsView() {
        zoomInButton.setPrefWidth(40);
        zoomInButton.setPrefHeight(40);
        zoomOutButton.setPrefWidth(40);
        zoomOutButton.setPrefHeight(40);

        zoomInButton.setMinHeight(30);
        zoomOutButton.setMinHeight(30);
        zoomInButton.setMinWidth(30);
        zoomOutButton.setMinWidth(30);

        zoomInButton.setFont(Font.font("System", FontWeight.BOLD, 14));
        zoomOutButton.setFont(Font.font("System", FontWeight.BOLD, 14));

        container.setSpacing(3);
        container.getChildren().addAll(zoomInButton, zoomOutButton);
        container.setManaged(false);

        zoomInButton.setOnAction(e -> changeZoom(1.0));
        zoomOutButton.setOnAction(e -> changeZoom(-1.0));

        zoomInButton.setText("+");
        zoomOutButton.setText("−");
    }

    /**
     * Node to be placed at the bottom of the MapView.
     */
    public Node getNode() {
        return container;
    }

    private MapField getMapField() {
        return model != null ? model.getMapField() : null;
    }

    private void changeZoom(double zoomDelta) {
        MapField map = getMapField();
        if (map == null) {
            return;
        }

        map.setZoom(map.getZoom() + zoomDelta);
        publisher.publishEvent(new WhatChanged(this, WhatChanged.Change.mapzoom));
    }

    private void updateZoomButtons() {
        MapField map = getMapField();
        if (map == null) {
            return;
        }
        Platform.runLater(() -> {
            zoomOutButton.setDisable(map.getZoom() <= MapField.MIN_ZOOM);
            zoomInButton.setDisable(map.getZoom() >= MapField.MAX_ZOOM);
        });
    }

    public void adjustX(double parentWidth) {
        container.setLayoutX(parentWidth - container.prefWidth(-1) - 5);
    }

    public void adjustY(double parentHeight) {
        container.setLayoutY(parentHeight - container.prefHeight(-1) + 5);
    }

    @EventListener
    private void onZoomChanged(WhatChanged changed) {
        if (changed.isZoom()) {
            updateZoomButtons();
        }
    }
}
