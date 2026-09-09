package com.ugcs.geohammer.chart.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.service.script.ScriptParameter;
import com.ugcs.geohammer.view.Views;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextFormatter.Change;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.jspecify.annotations.Nullable;

public class LineSelector extends VBox {

	private static final int MAX_LINE_DIGITS = 4;

	private final Spinner<Integer> lineSpinner;

	private final Label parameterLabel;

	private final Button useSelectedButton;

	private final ScriptParameter param;

	private final Supplier<Integer> chartLineIndex;

	private List<Integer> lineIndexes = List.of();

	public LineSelector(ScriptParameter param, @Nullable SgyFile file,
			Supplier<Integer> chartLineIndex) {
		super(Views.TOP_LABEL_SPACING);

		this.param = param;
		this.chartLineIndex = chartLineIndex;

		lineSpinner = new Spinner<>(new IntegerSpinnerValueFactory(0, 0, 0));
		lineSpinner.setEditable(true);
		lineSpinner.setPrefWidth(80.0);
		lineSpinner.getEditor().setTextFormatter(new TextFormatter<>(this::acceptLineIndex));
		lineSpinner.getEditor().textProperty().addListener(
				(property, was, text) -> selectLineFromEditor(text));

		parameterLabel = new Label();
		parameterLabel.getStyleClass().addAll(Views.TOP_LABEL_STYLES);
		parameterLabel.setPadding(Views.TOP_LABEL_INSETS);

		useSelectedButton = new Button("Use selected");
		useSelectedButton.setTooltip(
				new Tooltip("Use the line selected on the chart or on the map"));
		useSelectedButton.setOnAction(e -> selectLineFromChart());

		HBox inputRow = new HBox(Views.DEFAULT_SPACING,
				lineSpinner, Views.createSpacer(), useSelectedButton);
		inputRow.setAlignment(Pos.CENTER_LEFT);

		getChildren().addAll(parameterLabel, inputRow);

		setFile(file);
	}

	public void setFile(@Nullable SgyFile file) {
		lineIndexes = file != null
				? new ArrayList<>(file.getLineRanges().keySet())
				: List.of();
		if (lineIndexes.isEmpty()) {
			showNoLines();
		} else {
			showLines();
		}
	}

	@Nullable
	public Integer getSelectedLineIndex() {
		return lineIndexes.isEmpty() ? null : lineSpinner.getValue();
	}

	private void showNoLines() {
		lineSpinner.setDisable(true);
		useSelectedButton.setDisable(true);
		updateLabel("no lines");
	}

	private void showLines() {
		int first = lineIndexes.getFirst();
		int last = lineIndexes.getLast();

		lineSpinner.setDisable(false);
		useSelectedButton.setDisable(false);
		updateLabel(first + "-" + last);
		updateBounds(first, last);
	}

	private void updateLabel(String range) {
		parameterLabel.setText(param.getLabel(" (" + range + ")"));
	}

	private void updateBounds(int first, int last) {
		Integer selected = lineSpinner.getValue();
		int line = selected != null ? Math.clamp(selected, first, last) : first;
		lineSpinner.setValueFactory(new IntegerSpinnerValueFactory(first, last, line));
	}

	// intermediate states of typing are unavoidable, so the editor admits an empty field
	// and any digits; a number out of range is corrected right away
	@Nullable
	private Change acceptLineIndex(Change change) {
		String text = change.getControlNewText();
		if (text.isEmpty()) {
			return change;
		}
		if (text.length() > MAX_LINE_DIGITS || text.length() > 1 && text.charAt(0) == '0') {
			return null;
		}
		for (int i = 0; i < text.length(); i++) {
			if (!Character.isDigit(text.charAt(i))) {
				return null;
			}
		}
		return change;
	}

	private void selectLineFromEditor(String text) {
		if (text.isEmpty() || lineIndexes.isEmpty()) {
			return;
		}
		int typed = Integer.parseInt(text);
		int line = Math.clamp(typed, lineIndexes.getFirst(), lineIndexes.getLast());
		lineSpinner.getValueFactory().setValue(line);
		if (line != typed) {
			lineSpinner.getEditor().setText(String.valueOf(line));
		}
	}

	private void selectLineFromChart() {
		Integer line = chartLineIndex.get();
		if (line != null && lineIndexes.contains(line)) {
			lineSpinner.getValueFactory().setValue(line);
		}
	}
}
