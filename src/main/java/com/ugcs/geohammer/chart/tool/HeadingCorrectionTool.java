package com.ugcs.geohammer.chart.tool;

import com.ugcs.geohammer.chart.csv.SensorLineChart;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
import com.ugcs.geohammer.model.event.SeriesUpdatedEvent;
import com.ugcs.geohammer.service.magnetics.HeadingCorrectionOptions;
import com.ugcs.geohammer.service.magnetics.HeadingCorrectionResult;
import com.ugcs.geohammer.service.magnetics.HeadingCorrectionService;
import com.ugcs.geohammer.service.magnetics.MagneticProcessingWorkflow;
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
public class HeadingCorrectionTool extends FilterToolView {

    private static final int DEFAULT_HEADING_BINS = 8;

    private final Model model;

    private final HeadingCorrectionService correctionService;

    private final MagneticProcessingWorkflow processingWorkflow;

    private final ComboBox<String> inputSeriesSelector;

    private final TextField headingBinsInput;

    private final TextField outputSeriesInput;

    private final Label diagnostics;

    public HeadingCorrectionTool(Model model, HeadingCorrectionService correctionService,
                                 MagneticProcessingWorkflow processingWorkflow, ExecutorService executor) {
        super(executor);
        this.model = model;
        this.correctionService = correctionService;
        this.processingWorkflow = processingWorkflow;

        InputWithTopLabel headingBins = new InputWithTopLabel("Heading bins");
        headingBinsInput = headingBins.getInput();
        headingBinsInput.setText(Integer.toString(DEFAULT_HEADING_BINS));
        headingBinsInput.setPromptText("4 to 36");
        headingBinsInput.textProperty().addListener((observable, oldValue, newValue) -> validateInput());

        InputWithTopLabel outputSeries = new InputWithTopLabel("Heading-corrected series");
        outputSeriesInput = outputSeries.getInput();
        outputSeriesInput.textProperty().addListener((observable, oldValue, newValue) -> validateInput());

        inputSeriesSelector = new ComboBox<>();
        inputSeriesSelector.setMaxWidth(Double.MAX_VALUE);
        inputSeriesSelector.setOnAction(event -> {
            String series = inputSeriesSelector.getValue();
            if (series != null) {
                outputSeriesInput.setText(series + "_HEADING");
            }
            validateInput();
        });

        diagnostics = new Label();
        inputContainer.getChildren().setAll(
                new NodeWithTopLabel<>("Magnetic series", inputSeriesSelector),
                headingBins,
                new Label("Uses crossover differences to estimate heading-dependent bias."),
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
        boolean invalid = inputSeriesSelector.getValue() == null || outputSeriesInput.getText().isBlank()
                || outputSeriesInput.getText().equals(inputSeriesSelector.getValue()) || parseHeadingBins() == null;
        disableActions(invalid);
    }

    private Integer parseHeadingBins() {
        try {
            int headingBins = Integer.parseInt(headingBinsInput.getText().trim());
            return headingBins >= 4 && headingBins <= 36 ? headingBins : null;
        } catch (NumberFormatException e) {
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
        Integer headingBins = parseHeadingBins();
        if (inputSeries == null || headingBins == null) {
            return;
        }
        submitAction(() -> {
            try {
                HeadingCorrectionResult result = correctionService.correct(chart.getFile().getGeoData(), inputSeries,
                        headingBins);
                chart.createDerivedSeries(outputSeries, inputSeries, result.values());
                processingWorkflow.recordHeadingCorrection(chart.getFile(),
                        new HeadingCorrectionOptions(inputSeries, outputSeries, headingBins));
                Platform.runLater(() -> diagnostics.setText("Crossovers: %d, residual RMS: %.2f"
                        .formatted(result.crossovers().size(), result.rmsError())));
            } catch (IllegalArgumentException e) {
                Platform.runLater(() -> Dialogs.showError("Heading correction", e.getMessage()));
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
