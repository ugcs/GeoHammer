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
import javafx.scene.text.Font;
import javafx.scene.text.Text;

public class RulerMeasurementView extends BorderPane {

  	private static final String DISTANCE_VALUE_SAMPLE = "00.00";
	private static final String HEADING_VALUE_SAMPLE = "000.0°";

	private final MapRuler mapRuler;
	private final ComboBox<String> unitComboBox;

	private final Label distanceLabel = new Label();
	private final Label headingLabel = new Label();

	public RulerMeasurementView(MapRuler mapRuler) {
		this.mapRuler = mapRuler;
		unitComboBox = new ComboBox<>(
				FXCollections.observableArrayList(
						TraceUnit.distanceUnits().stream()
								.map(TraceUnit::getLabel)
								.toList())
		);
		distanceLabel.setMinWidth(measureTextWidth(DISTANCE_VALUE_SAMPLE, distanceLabel.getFont()));
		headingLabel.setMinWidth(measureTextWidth(HEADING_VALUE_SAMPLE, headingLabel.getFont()));
		Label headingCaption = new Label("Heading:");
		HBox.setMargin(headingCaption, new Insets(0, 0, 0, 20));

		unitComboBox.setValue(TraceUnit.METERS.getLabel());
		unitComboBox.setPrefWidth(65);

		distanceLabel.setPrefHeight(30);
		headingLabel.setPrefHeight(30);
		unitComboBox.setPrefHeight(20);

		HBox hBox = new HBox(4, new Label("Distance:"), distanceLabel, unitComboBox, headingCaption, headingLabel);
		hBox.setAlignment(Pos.CENTER_LEFT);
		hBox.setPadding(new Insets(0, 0, 0, 10));
		setBottom(hBox);

		unitComboBox.valueProperty().addListener((obs, oldVal, newVal) -> update());
		update();
	}

	public void update() {
		Double distanceMeters = mapRuler.getDistanceMeters();
		Double heading = mapRuler.getHeading();
		if (distanceMeters == null || heading == null) {
			distanceLabel.setText("");
			headingLabel.setText("");
			return;
		}
		TraceUnit distanceUnit = findDistanceUnit(unitComboBox.getValue());
		distanceLabel.setText(formatDistance(distanceMeters, distanceUnit));
		headingLabel.setText(formatHeading(heading));
	}

	private static TraceUnit findDistanceUnit(String label) {
		for (TraceUnit unit : TraceUnit.distanceUnits()) {
			if (unit.getLabel().equals(label)) {
				return unit;
			}
		}
		return TraceUnit.getDefault();
	}

	private static String formatDistance(double meters, TraceUnit unit) {
		return String.format("%.2f", TraceUnit.convert(meters, unit));
	}

	private static String formatHeading(double heading) {
		// round before wrapping, so 359.96 shows as 0.0 instead of 360.0
		double rounded = Math.round(heading * 10) / 10.0;
		return String.format("%.1f°", rounded % 360);
	}

	private static double measureTextWidth(String text, Font font) {
		Text sample = new Text(text);
		sample.setFont(font);
		return Math.ceil(sample.getLayoutBounds().getWidth());
	}
}
