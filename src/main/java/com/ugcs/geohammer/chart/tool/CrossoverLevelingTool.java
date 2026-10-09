package com.ugcs.geohammer.chart.tool;

import com.ugcs.geohammer.chart.csv.SensorLineChart;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
import com.ugcs.geohammer.service.magnetics.CrossoverLevelingResult;
import com.ugcs.geohammer.service.magnetics.CrossoverLevelingService;
import com.ugcs.geohammer.view.Dialogs;
import com.ugcs.geohammer.view.control.InputWithTopLabel;
import com.ugcs.geohammer.view.control.NodeWithTopLabel;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;

@Component
public class CrossoverLevelingTool extends FilterToolView {

    private final Model model;

    private final CrossoverLevelingService levelingService;

    private final ComboBox<String> inputSeriesSelector;

    private final TextField tieLinesInput;

    private final TextField outputSeriesInput;

    private final Label availableLines;

    private final Label diagnostics;

    public CrossoverLevelingTool(Model model, CrossoverLevelingService levelingService, ExecutorService executor) {
        super(executor);
        this.model = model;
        this.levelingService = levelingService;

        InputWithTopLabel outputSeries = new InputWithTopLabel("Crossover-leveled series");
        outputSeriesInput = outputSeries.getInput();
        outputSeriesInput.textProperty().addListener((observable, oldValue, newValue) -> validateInput());

        inputSeriesSelector = new ComboBox<>();
        inputSeriesSelector.setMaxWidth(Double.MAX_VALUE);
        inputSeriesSelector.setOnAction(event -> {
            String series = inputSeriesSelector.getValue();
            if (series != null) {
                outputSeriesInput.setText(series + "_XOVER");
            }
            validateInput();
        });

        InputWithTopLabel tieLines = new InputWithTopLabel("Tie-line IDs");
        tieLinesInput = tieLines.getInput();
        tieLinesInput.setPromptText("For example: 5, 10, 15");
        tieLinesInput.textProperty().addListener((observable, oldValue, newValue) -> validateInput());

        availableLines = new Label();
        diagnostics = new Label();

        inputContainer.getChildren().setAll(
                new NodeWithTopLabel<>("Magnetic series", inputSeriesSelector),
                tieLines,
                availableLines,
                outputSeries,
                diagnostics
        );
        showApply(true);
    }

    @Override
    public boolean isVisibleFor(SgyFile file) {
        return file instanceof CsvFile;
    }

    @Override
    public void updateView() {
        inputSeriesSelector.getItems().clear();
        diagnostics.setText("");
        availableLines.setText("");
        if (model.getChart(selectedFile) instanceof SensorLineChart chart) {
            inputSeriesSelector.getItems().addAll(chart.getSeriesNames());
            String selectedSeries = chart.getSelectedSeriesName();
            if (selectedSeries != null) {
                inputSeriesSelector.setValue(selectedSeries);
            } else if (!inputSeriesSelector.getItems().isEmpty()) {
                inputSeriesSelector.setValue(inputSeriesSelector.getItems().getFirst());
            }
            availableLines.setText("Available line IDs: " + chart.getFile().getLineRanges().keySet());
        }
        validateInput();
    }

    private void validateInput() {
        Set<Integer> tieLines = parseTieLines();
        boolean invalid = inputSeriesSelector.getValue() == null || tieLines.isEmpty()
                || outputSeriesInput.getText().isBlank() || outputSeriesInput.getText().equals(inputSeriesSelector.getValue());
        disableActions(invalid);
    }

    private Set<Integer> parseTieLines() {
        Set<Integer> lines = new LinkedHashSet<>();
        String[] values = tieLinesInput.getText().split(",");
        for (String value : values) {
            String trimmed = value.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                lines.add(Integer.parseInt(trimmed));
            } catch (NumberFormatException e) {
                return Set.of();
            }
        }
        return lines;
    }

    @Override
    protected void onApply(ActionEvent event) {
        if (!(model.getChart(selectedFile) instanceof SensorLineChart chart)) {
            return;
        }
        String inputSeries = inputSeriesSelector.getValue();
        String outputSeries = outputSeriesInput.getText().trim();
        Set<Integer> tieLines = parseTieLines();
        if (inputSeries == null || tieLines.isEmpty()) {
            return;
        }
        submitAction(() -> {
            try {
                CrossoverLevelingResult result = levelingService.level(chart.getFile().getGeoData(), inputSeries, tieLines);
                chart.createDerivedSeries(outputSeries, inputSeries, result.values());
                Platform.runLater(() -> diagnostics.setText("Crossovers: %d, RMS error: %.2f"
                        .formatted(result.crossovers().size(), result.rmsError())));
            } catch (IllegalArgumentException e) {
                Platform.runLater(() -> Dialogs.showError("Crossover leveling", e.getMessage()));
            }
            return null;
        });
    }

    @EventListener
    private void onFileSelected(FileSelectedEvent event) {
        Platform.runLater(() -> selectFile(event.getFile()));
    }
}
