package com.ugcs.geohammer.map;


import com.ugcs.geohammer.map.layer.MapRuler;
import com.ugcs.geohammer.model.TraceUnit;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

public class DistanceLabelPane extends BorderPane {

	public DistanceLabelPane(MapRuler mapRuler, Runnable updateUI, Runnable visibilityChanged) {
		ComboBox<String> unitComboBox = new ComboBox<>(
				FXCollections.observableArrayList(
						TraceUnit.distanceUnits().stream()
								.map(TraceUnit::getLabel)
								.toList())
		);
		Label distanceLabel = new Label();

		unitComboBox.setValue(TraceUnit.METERS.getLabel());
		unitComboBox.setPrefWidth(65);

		distanceLabel.setPrefHeight(30);
		unitComboBox.setPrefHeight(20);

		HBox hBox = new HBox(10, distanceLabel, unitComboBox);
		hBox.setAlignment(Pos.CENTER_LEFT);
		hBox.setPadding(new Insets(0, 0, 0, 10));
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
			TraceUnit traceUnit = TraceUnit.distanceUnits().stream()
					.filter(u -> u.getLabel().equals(newVal))
					.findFirst()
					.orElse(TraceUnit.METERS);
			mapRuler.setDistanceUnit(traceUnit);
			updateDistanceLabel.run();
		});

		mapRuler.setRepaintCallback(() -> {
			updateUI.run();
			updateDistanceLabel.run();
		});
		updateDistanceLabel.run();
	}
}
