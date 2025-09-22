package com.ugcs.gprvisualizer.draw.map;

import java.util.Collections;
import java.util.List;

import com.github.thecoldwine.sigrun.common.ext.MapField;
import com.ugcs.gprvisualizer.draw.Layer;
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
public class ZoomButtonLayer implements Layer {

	private final VBox container = new VBox();

	@Autowired
	private Model model;

	@Autowired
	private ApplicationEventPublisher publisher;

	private final Button zoomInButton = new Button();
	private final Button zoomOutButton = new Button();

	public ZoomButtonLayer() {
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

		container.getStyleClass().add("zoom-layer");
		container.setSpacing(3);
		container.getChildren().addAll(zoomInButton, zoomOutButton);

		zoomInButton.setOnAction(e -> changeZoom(1.1));
		zoomOutButton.setOnAction(e -> changeZoom(1.0 / 1.1));

		zoomInButton.setText("+");
		zoomOutButton.setText("-");
	}

	private void changeZoom(double factor) {
		if (!isMapActive()) {
			return;
		}
		MapField mf = model.getMapField();
		double newZoom = Math.max(MapField.MIN_ZOOM, Math.min(MapField.MAX_ZOOM, mf.getZoom() * factor));
		mf.setZoom(newZoom);
		publisher.publishEvent(new WhatChanged(this, WhatChanged.Change.mapzoom));
	}

	private boolean isMapActive() {
		return model != null && model.getMapField() != null;
	}

	private void refreshZoomButtons() {
		MapField mapField = model.getMapField();
		if (mapField == null) {
			return;
		}
		Platform.runLater(() -> {
			zoomOutButton.setDisable(mapField.getZoom() <= MapField.MIN_ZOOM);
			zoomInButton.setDisable(mapField.getZoom() >= MapField.MAX_ZOOM);
		});
	}

	/**
	 * Node to be placed at the bottom of the MapView.
	 */
	public Node getNode() {
		return container;
	}

	@Override
	public void draw(java.awt.Graphics2D g2, MapField field) {
		// No drawing; purely a control layer.
	}

	@Override
	public List<Node> getToolNodes() {
		// Not shown in the toolbar.
		return Collections.emptyList();
	}

	@EventListener
	private void listenZoomChange(WhatChanged changed) {
		if (changed.isZoom() && isMapActive()) {
			refreshZoomButtons();
		}
	}
}
