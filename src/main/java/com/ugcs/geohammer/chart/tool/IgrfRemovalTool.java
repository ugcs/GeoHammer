package com.ugcs.geohammer.chart.tool;

import com.ugcs.geohammer.chart.csv.SensorLineChart;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
import com.ugcs.geohammer.service.magnetics.IgrfRemovalOptions;
import com.ugcs.geohammer.service.magnetics.IgrfRemovalService;
import com.ugcs.geohammer.service.magnetics.MagneticProcessingWorkflow;
import com.ugcs.geohammer.view.Dialogs;
import com.ugcs.geohammer.view.control.InputWithTopLabel;
import com.ugcs.geohammer.view.control.NodeWithTopLabel;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutorService;

@Component
public class IgrfRemovalTool extends FilterToolView {

    private final Model model;

    private final IgrfRemovalService removalService;

    private final MagneticProcessingWorkflow processingWorkflow;

    private final ComboBox<String> inputSeriesSelector;

    private final TextField outputSeriesInput;

    private final TextField fallbackDateInput;

    public IgrfRemovalTool(Model model, IgrfRemovalService removalService,
                           MagneticProcessingWorkflow processingWorkflow, ExecutorService executor) {
        super(executor);
        this.model = model;
        this.removalService = removalService;
        this.processingWorkflow = processingWorkflow;

        InputWithTopLabel outputSeries = new InputWithTopLabel("IGRF-corrected series");
        outputSeriesInput = outputSeries.getInput();
        outputSeriesInput.textProperty().addListener((observable, oldValue, newValue) -> validateInput());

        inputSeriesSelector = new ComboBox<>();
        inputSeriesSelector.setMaxWidth(Double.MAX_VALUE);
        inputSeriesSelector.setOnAction(event -> {
            String series = inputSeriesSelector.getValue();
            if (series != null) {
                outputSeriesInput.setText(series + "_IGRF");
            }
            validateInput();
        });

        InputWithTopLabel fallbackDate = new InputWithTopLabel("Fallback date, UTC (optional)");
        fallbackDateInput = fallbackDate.getInput();
        fallbackDateInput.setPromptText("YYYY-MM-DD");
        fallbackDateInput.textProperty().addListener((observable, oldValue, newValue) -> validateInput());

        inputContainer.getChildren().setAll(
                new NodeWithTopLabel<>("Magnetic series", inputSeriesSelector),
                outputSeries,
                fallbackDate
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
        if (model.getChart(selectedFile) instanceof SensorLineChart chart) {
            inputSeriesSelector.getItems().addAll(chart.getSeriesNames());
            String selectedSeries = chart.getSelectedSeriesName();
            if (selectedSeries != null) {
                inputSeriesSelector.setValue(selectedSeries);
            } else if (!inputSeriesSelector.getItems().isEmpty()) {
                inputSeriesSelector.setValue(inputSeriesSelector.getItems().getFirst());
            }
        }
        validateInput();
    }

    private void validateInput() {
        boolean invalidDate = !fallbackDateInput.getText().isBlank() && parseFallbackTimestamp() == null;
        boolean invalid = inputSeriesSelector.getValue() == null || outputSeriesInput.getText().isBlank()
                || outputSeriesInput.getText().equals(inputSeriesSelector.getValue()) || invalidDate;
        disableActions(invalid);
    }

    private Instant parseFallbackTimestamp() {
        String text = fallbackDateInput.getText().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(text).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    protected void onApply(ActionEvent event) {
        if (!(model.getChart(selectedFile) instanceof SensorLineChart chart)) {
            return;
        }
        String inputSeries = inputSeriesSelector.getValue();
        String outputSeries = outputSeriesInput.getText().trim();
        Instant fallbackTimestamp = parseFallbackTimestamp();
        if (inputSeries == null) {
            return;
        }
        submitAction(() -> {
            try {
                chart.createDerivedSeries(outputSeries, inputSeries,
                        removalService.remove(chart.getFile().getGeoData(), inputSeries, fallbackTimestamp));
                processingWorkflow.recordIgrfRemoval(chart.getFile(),
                        new IgrfRemovalOptions(inputSeries, outputSeries, fallbackTimestamp));
            } catch (IllegalArgumentException e) {
                Platform.runLater(() -> Dialogs.showError("IGRF removal", e.getMessage()));
            }
            return null;
        });
    }

    @EventListener
    private void onFileSelected(FileSelectedEvent event) {
        Platform.runLater(() -> selectFile(event.getFile()));
    }
}
