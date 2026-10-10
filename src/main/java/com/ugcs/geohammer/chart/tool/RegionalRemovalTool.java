package com.ugcs.geohammer.chart.tool;

import com.ugcs.geohammer.chart.csv.SensorLineChart;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
import com.ugcs.geohammer.model.event.SeriesUpdatedEvent;
import com.ugcs.geohammer.service.magnetics.MagneticProcessingWorkflow;
import com.ugcs.geohammer.service.magnetics.RegionalRemovalOptions;
import com.ugcs.geohammer.service.magnetics.RegionalRemovalResult;
import com.ugcs.geohammer.service.magnetics.RegionalRemovalService;
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

import java.util.concurrent.ExecutorService;

@Component
public class RegionalRemovalTool extends FilterToolView {

    private final Model model;

    private final RegionalRemovalService removalService;

    private final MagneticProcessingWorkflow processingWorkflow;

    private final ComboBox<String> inputSeriesSelector;

    private final ComboBox<Integer> polynomialOrderSelector;

    private final TextField outputSeriesInput;

    private final Label diagnostics;

    public RegionalRemovalTool(Model model, RegionalRemovalService removalService,
                               MagneticProcessingWorkflow processingWorkflow, ExecutorService executor) {
        super(executor);
        this.model = model;
        this.removalService = removalService;
        this.processingWorkflow = processingWorkflow;

        InputWithTopLabel outputSeries = new InputWithTopLabel("Residual anomaly series");
        outputSeriesInput = outputSeries.getInput();
        outputSeriesInput.textProperty().addListener((observable, oldValue, newValue) -> validateInput());

        inputSeriesSelector = new ComboBox<>();
        inputSeriesSelector.setMaxWidth(Double.MAX_VALUE);
        inputSeriesSelector.setOnAction(event -> {
            String series = inputSeriesSelector.getValue();
            if (series != null) {
                outputSeriesInput.setText(series + "_RESIDUAL");
            }
            validateInput();
        });

        polynomialOrderSelector = new ComboBox<>();
        polynomialOrderSelector.getItems().setAll(0, 1, 2, 3);
        polynomialOrderSelector.setValue(1);
        polynomialOrderSelector.setMaxWidth(Double.MAX_VALUE);
        polynomialOrderSelector.setOnAction(event -> validateInput());

        diagnostics = new Label();
        inputContainer.getChildren().setAll(
                new NodeWithTopLabel<>("Magnetic series", inputSeriesSelector),
                new NodeWithTopLabel<>("Regional polynomial order", polynomialOrderSelector),
                new Label("Fits a spatial regional field using all coordinates, then outputs the residual anomaly."),
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
        diagnostics.setText("");
        updateInputSeries(null);
    }

    private void updateInputSeries(String preferredSeries) {
        String selectedSeries = preferredSeries;
        if (selectedSeries == null && model.getChart(selectedFile) instanceof SensorLineChart chart) {
            selectedSeries = chart.getSelectedSeriesName();
        }
        MagneticSeriesSelector.update(inputSeriesSelector, selectedFile, selectedSeries);
        validateInput();
    }

    private void validateInput() {
        boolean invalid = inputSeriesSelector.getValue() == null || polynomialOrderSelector.getValue() == null
                || outputSeriesInput.getText().isBlank() || outputSeriesInput.getText().equals(inputSeriesSelector.getValue());
        disableActions(invalid);
    }

    @Override
    protected void onApply(ActionEvent event) {
        if (!(model.getChart(selectedFile) instanceof SensorLineChart chart)) {
            return;
        }
        String inputSeries = inputSeriesSelector.getValue();
        String outputSeries = outputSeriesInput.getText().trim();
        Integer polynomialOrder = polynomialOrderSelector.getValue();
        if (inputSeries == null || polynomialOrder == null) {
            return;
        }
        submitAction(() -> {
            try {
                RegionalRemovalResult result = removalService.remove(chart.getFile().getGeoData(), inputSeries,
                        polynomialOrder);
                chart.createDerivedSeries(outputSeries, inputSeries, result.values());
                processingWorkflow.recordRegionalRemoval(chart.getFile(),
                        new RegionalRemovalOptions(inputSeries, outputSeries, polynomialOrder));
                Platform.runLater(() -> diagnostics.setText("Fit samples: %d, residual RMS: %.2f"
                        .formatted(result.sampleCount(), result.rmsError())));
            } catch (IllegalArgumentException e) {
                Platform.runLater(() -> Dialogs.showError("Regional removal", e.getMessage()));
            }
            return null;
        });
    }

    @EventListener
    private void onFileSelected(FileSelectedEvent event) {
        Platform.runLater(() -> selectFile(event.getFile()));
    }

    @EventListener
    private void onSeriesUpdated(SeriesUpdatedEvent event) {
        if (!event.getFile().equals(selectedFile)) {
            return;
        }
        Platform.runLater(() -> {
            if (event.getFile().equals(selectedFile)) {
                updateInputSeries(event.getSeriesName());
            }
        });
    }
}
