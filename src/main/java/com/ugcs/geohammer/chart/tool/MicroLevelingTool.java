package com.ugcs.geohammer.chart.tool;

import com.ugcs.geohammer.chart.csv.SensorLineChart;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
import com.ugcs.geohammer.service.magnetics.MagneticProcessingWorkflow;
import com.ugcs.geohammer.service.magnetics.MicroLevelingOptions;
import com.ugcs.geohammer.service.magnetics.MicroLevelingService;
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
public class MicroLevelingTool extends FilterToolView {

    private static final int DEFAULT_WINDOW_SIZE = 21;

    private final Model model;

    private final MicroLevelingService levelingService;

    private final MagneticProcessingWorkflow processingWorkflow;

    private final ComboBox<String> inputSeriesSelector;

    private final TextField windowSizeInput;

    private final TextField outputSeriesInput;

    public MicroLevelingTool(Model model, MicroLevelingService levelingService,
                             MagneticProcessingWorkflow processingWorkflow, ExecutorService executor) {
        super(executor);
        this.model = model;
        this.levelingService = levelingService;
        this.processingWorkflow = processingWorkflow;

        InputWithTopLabel windowSize = new InputWithTopLabel("Residual window (samples)");
        windowSizeInput = windowSize.getInput();
        windowSizeInput.setText(Integer.toString(DEFAULT_WINDOW_SIZE));
        windowSizeInput.setPromptText("Odd number, e.g. 21");
        windowSizeInput.textProperty().addListener((observable, oldValue, newValue) -> validateInput());

        InputWithTopLabel outputSeries = new InputWithTopLabel("Micro-leveled series");
        outputSeriesInput = outputSeries.getInput();
        outputSeriesInput.textProperty().addListener((observable, oldValue, newValue) -> validateInput());

        inputSeriesSelector = new ComboBox<>();
        inputSeriesSelector.setMaxWidth(Double.MAX_VALUE);
        inputSeriesSelector.setOnAction(event -> {
            String series = inputSeriesSelector.getValue();
            if (series != null) {
                outputSeriesInput.setText(series + "_MICRO");
            }
            validateInput();
        });

        inputContainer.getChildren().setAll(
                new NodeWithTopLabel<>("Magnetic series", inputSeriesSelector),
                windowSize,
                new Label("Corrects slowly varying residuals between adjacent survey lines."),
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
        boolean invalid = inputSeriesSelector.getValue() == null || outputSeriesInput.getText().isBlank()
                || outputSeriesInput.getText().equals(inputSeriesSelector.getValue()) || parseWindowSize() == null;
        disableActions(invalid);
    }

    private Integer parseWindowSize() {
        try {
            int windowSize = Integer.parseInt(windowSizeInput.getText().trim());
            return windowSize >= 3 && windowSize % 2 == 1 ? windowSize : null;
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
        Integer windowSize = parseWindowSize();
        if (inputSeries == null || windowSize == null) {
            return;
        }
        submitAction(() -> {
            try {
                chart.createDerivedSeries(outputSeries, inputSeries,
                        levelingService.level(chart.getFile().getGeoData(), inputSeries, windowSize));
                processingWorkflow.recordMicroLeveling(chart.getFile(),
                        new MicroLevelingOptions(inputSeries, outputSeries, windowSize));
            } catch (IllegalArgumentException e) {
                Platform.runLater(() -> Dialogs.showError("Micro-leveling", e.getMessage()));
            }
            return null;
        });
    }

    @EventListener
    private void onFileSelected(FileSelectedEvent event) {
        Platform.runLater(() -> selectFile(event.getFile()));
    }
}
