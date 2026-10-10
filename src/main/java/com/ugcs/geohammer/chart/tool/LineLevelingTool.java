package com.ugcs.geohammer.chart.tool;

import com.ugcs.geohammer.chart.csv.SensorLineChart;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
import com.ugcs.geohammer.model.event.SeriesUpdatedEvent;
import com.ugcs.geohammer.service.magnetics.LineLevelingOptions;
import com.ugcs.geohammer.service.magnetics.LineLevelingService;
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

import java.util.concurrent.ExecutorService;

@Component
public class LineLevelingTool extends FilterToolView {

    private final Model model;

    private final LineLevelingService levelingService;

    private final MagneticProcessingWorkflow processingWorkflow;

    private final ComboBox<String> inputSeriesSelector;

    private final TextField outputSeriesInput;

    public LineLevelingTool(Model model, LineLevelingService levelingService,
                            MagneticProcessingWorkflow processingWorkflow, ExecutorService executor) {
        super(executor);
        this.model = model;
        this.levelingService = levelingService;
        this.processingWorkflow = processingWorkflow;

        InputWithTopLabel outputSeries = new InputWithTopLabel("Leveled series");
        outputSeriesInput = outputSeries.getInput();
        outputSeriesInput.textProperty().addListener((observable, oldValue, newValue) -> validateInput());

        inputSeriesSelector = new ComboBox<>();
        inputSeriesSelector.setMaxWidth(Double.MAX_VALUE);
        inputSeriesSelector.setOnAction(event -> {
            String series = inputSeriesSelector.getValue();
            if (series != null) {
                outputSeriesInput.setText(series + "_LEVELLED");
            }
            validateInput();
        });

        inputContainer.getChildren().setAll(
                new NodeWithTopLabel<>("Magnetic series", inputSeriesSelector),
                outputSeries
        );
        showApply(true);
    }

    @Override
    public boolean isVisibleFor(SgyFile file) {
        return file instanceof CsvFile;
    }

    @Override
    public void updateView() {
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
        boolean invalid = inputSeriesSelector.getValue() == null || outputSeriesInput.getText().isBlank()
                || outputSeriesInput.getText().equals(inputSeriesSelector.getValue());
        disableActions(invalid);
    }

    @Override
    protected void onApply(ActionEvent event) {
        if (!(model.getChart(selectedFile) instanceof SensorLineChart chart)) {
            return;
        }
        String inputSeries = inputSeriesSelector.getValue();
        String outputSeries = outputSeriesInput.getText().trim();
        if (inputSeries == null) {
            return;
        }
        submitAction(() -> {
            try {
                chart.createDerivedSeries(outputSeries, inputSeries,
                        levelingService.level(chart.getFile().getGeoData(), inputSeries));
                processingWorkflow.recordLineLeveling(chart.getFile(),
                        new LineLevelingOptions(inputSeries, outputSeries));
            } catch (IllegalArgumentException e) {
                Platform.runLater(() -> Dialogs.showError("Line leveling", e.getMessage()));
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
