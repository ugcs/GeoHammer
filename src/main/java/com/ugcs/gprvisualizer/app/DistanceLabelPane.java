package com.ugcs.gprvisualizer.app;

import java.util.Arrays;

import com.ugcs.gprvisualizer.app.service.DistanceConverterService;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

public class DistanceLabelPane extends BorderPane {
	private final Label distanceLabel = new Label();

	public DistanceLabelPane(MapRuler mapRuler, Runnable updateUI, Runnable visibilityChanged) {
		ComboBox<String> unitComboBox = new ComboBox<>(
				FXCollections.observableArrayList(
						Arrays.stream(DistanceConverterService.Unit.values())
								.map(DistanceConverterService::getUnitLabel)
								.toList())
		);
		unitComboBox.setValue(DistanceConverterService.getUnitLabel(DistanceConverterService.Unit.METERS));
		unitComboBox.setPrefWidth(65);

		distanceLabel.setPrefHeight(30);
		unitComboBox.setPrefHeight(20);

		HBox hBox = new HBox(10, distanceLabel, unitComboBox);
		hBox.setAlignment(Pos.CENTER_LEFT);
		setBottom(hBox);

		Runnable updateDistanceLabel = () -> {
			if (mapRuler.isVisible()) {
				distanceLabel.setText(mapRuler.getDistanceString());
				distanceLabel.setVisible(true);
				hBox.setVisible(true);
				visibilityChanged.run();
			} else {
				distanceLabel.setText("");
				distanceLabel.setVisible(false);
				hBox.setVisible(false);
				visibilityChanged.run();
			}
		};

		unitComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
			DistanceConverterService.Unit unit = Arrays.stream(DistanceConverterService.Unit.values())
					.filter(u -> DistanceConverterService.getUnitLabel(u).equals(newVal))
					.findFirst()
					.orElse(DistanceConverterService.Unit.METERS);
			mapRuler.setDistanceUnit(unit);
			updateDistanceLabel.run();
		});

		mapRuler.setRepaintCallback(() -> {
			updateUI.run();
			updateDistanceLabel.run();
		});
		updateDistanceLabel.run();
	}
}
