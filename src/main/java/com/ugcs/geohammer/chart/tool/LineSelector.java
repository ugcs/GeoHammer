package com.ugcs.geohammer.chart.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.view.Views;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.jspecify.annotations.Nullable;

public class LineSelector extends HBox {

	private static final String SELECT_PROMPT = "Select line";

	private static final String EMPTY_PROMPT = "No lines available";

	private final ComboBox<Integer> lines;

	private final Button detectButton;

	private final Supplier<Integer> selectedOnChart;

	public LineSelector(@Nullable SgyFile file, Supplier<Integer> selectedOnChart) {
		super(Views.DEFAULT_SPACING);

		this.selectedOnChart = selectedOnChart;

		lines = new ComboBox<>();
		lines.setPromptText(SELECT_PROMPT);
		lines.setMaxWidth(Double.MAX_VALUE);
		lines.setCellFactory(items -> createCell());
		lines.setButtonCell(createCell());
		HBox.setHgrow(lines, Priority.ALWAYS);

		detectButton = new Button("Detect");
		detectButton.setTooltip(new Tooltip("Select the line picked on the chart or on the map"));
		detectButton.setOnAction(e -> selectLineFromChart());

		getChildren().addAll(lines, detectButton);

		setFile(file);
	}

	public void setFile(@Nullable SgyFile file) {
		List<Integer> lineNumbers = file != null
				? new ArrayList<>(file.getLineRanges().keySet())
				: List.of();
		if (lineNumbers.isEmpty()) {
			showNoLines();
		} else {
			showLines(lineNumbers);
		}
	}

	@Nullable
	public Integer getSelectedLine() {
		return lines.getValue();
	}

	private void selectLineFromChart() {
		Integer line = selectedOnChart.get();
		if (line != null && lines.getItems().contains(line)) {
			lines.setValue(line);
		}
	}

	private void showNoLines() {
		lines.getItems().clear();
		lines.setValue(null);
		lines.setPromptText(EMPTY_PROMPT);
		lines.setDisable(true);
		detectButton.setDisable(true);
	}

	private void showLines(List<Integer> lineNumbers) {
		lines.setDisable(false);
		detectButton.setDisable(false);
		lines.setPromptText(SELECT_PROMPT);
		if (lines.getItems().equals(lineNumbers)) {
			return;
		}
		Integer selected = lines.getValue();
		lines.getItems().setAll(lineNumbers);
		lines.setValue(lineNumbers.contains(selected) ? selected : lineNumbers.getFirst());
	}

	private static ListCell<Integer> createCell() {
		return new ListCell<>() {
			@Override
			protected void updateItem(Integer item, boolean empty) {
				super.updateItem(item, empty);
				setText(item == null || empty ? null : "Line " + item);
			}
		};
	}
}
