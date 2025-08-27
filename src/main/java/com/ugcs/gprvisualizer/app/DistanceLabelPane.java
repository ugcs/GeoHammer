package com.ugcs.gprvisualizer.app;


import com.ugcs.gprvisualizer.app.service.DistanceConverterService;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

public class DistanceLabelPane extends BorderPane {

	public DistanceLabelPane(MapRuler mapRuler, Runnable updateUI, Runnable visibilityChanged) {
		ComboBox<String> unitComboBox = new ComboBox<>(
				FXCollections.observableArrayList(
						DistanceConverterService.Unit.distanceUnits().stream()
								.map(DistanceConverterService.Unit::getLabel)
								.toList())
		);
		Label distanceLabel = new Label();

		unitComboBox.setValue(DistanceConverterService.Unit.METERS.getLabel());
		unitComboBox.setPrefWidth(65);

		distanceLabel.setPrefHeight(30);
		unitComboBox.setPrefHeight(20);

		HBox hBox = new HBox(10, distanceLabel, unitComboBox);
		hBox.setAlignment(Pos.CENTER_LEFT);
		setBottom(hBox);

		Runnable updateDistanceLabel = () -> {
			if (mapRuler.isVisible()) {
				distanceLabel.setText(mapRuler.getFormattedDistance());
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
			DistanceConverterService.Unit unit = DistanceConverterService.Unit.distanceUnits().stream()
					.filter(u -> u.getLabel().equals(newVal))
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
