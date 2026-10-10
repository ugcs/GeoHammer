package com.ugcs.geohammer.chart.tool;

import com.ugcs.geohammer.chart.csv.SensorLineChart;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.event.FileOpenedEvent;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
import com.ugcs.geohammer.service.magnetics.DiurnalCorrectionOptions;
import com.ugcs.geohammer.service.magnetics.DiurnalCorrectionResult;
import com.ugcs.geohammer.service.magnetics.DiurnalCorrectionService;
import com.ugcs.geohammer.service.magnetics.MagneticProcessingWorkflow;
import com.ugcs.geohammer.view.Dialogs;
import com.ugcs.geohammer.view.control.InputWithTopLabel;
import com.ugcs.geohammer.view.control.NodeWithTopLabel;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;

@Component
public class DiurnalCorrectionTool extends FilterToolView {

    private final Model model;

    private final DiurnalCorrectionService correctionService;

    private final MagneticProcessingWorkflow processingWorkflow;

    private final ComboBox<String> surveySeriesSelector;

    private final ComboBox<SensorLineChart> baseStationSelector;

    private final ComboBox<String> baseStationSeriesSelector;

    private final CheckBox synchronizedBaseSeries = new CheckBox("Use synchronized base series in this survey");

    private final TextField outputSeriesInput;

    private final TextField referenceFieldInput;

    public DiurnalCorrectionTool(Model model, DiurnalCorrectionService correctionService,
                                 MagneticProcessingWorkflow processingWorkflow, ExecutorService executor) {
        super(executor);

        this.model = model;
        this.correctionService = correctionService;
        this.processingWorkflow = processingWorkflow;

        InputWithTopLabel outputSeries = new InputWithTopLabel("Corrected series");
        outputSeriesInput = outputSeries.getInput();
        outputSeriesInput.textProperty().addListener((observable, oldValue, newValue) -> validateInput());

        surveySeriesSelector = new ComboBox<>();
        surveySeriesSelector.setMaxWidth(Double.MAX_VALUE);
        surveySeriesSelector.setOnAction(event -> {
            String series = surveySeriesSelector.getValue();
            if (series != null) {
                outputSeriesInput.setText(series + "_DIURNAL");
            }
            validateInput();
        });

        baseStationSelector = new ComboBox<>();
        baseStationSelector.setMaxWidth(Double.MAX_VALUE);
        baseStationSelector.setConverter(new BaseStationConverter());
        baseStationSelector.setOnAction(event -> updateBaseStationSeries());

        baseStationSeriesSelector = new ComboBox<>();
        baseStationSeriesSelector.setMaxWidth(Double.MAX_VALUE);
        baseStationSeriesSelector.setOnAction(event -> validateInput());

        synchronizedBaseSeries.setOnAction(event -> updateBaseStationSeries());

        InputWithTopLabel referenceField = new InputWithTopLabel("Reference field (optional)");
        referenceFieldInput = referenceField.getInput();
        referenceFieldInput.setPromptText("Base-station median");
        referenceFieldInput.textProperty().addListener((observable, oldValue, newValue) -> validateInput());

        inputContainer.getChildren().setAll(
                new NodeWithTopLabel<>("Survey magnetic series", surveySeriesSelector),
                new NodeWithTopLabel<>("Base source", synchronizedBaseSeries),
                new NodeWithTopLabel<>("Base-station file", baseStationSelector),
                new NodeWithTopLabel<>("Base-station magnetic series", baseStationSeriesSelector),
                outputSeries,
                referenceField
        );
        showApply(true);
    }

    @Override
    public boolean isVisibleFor(SgyFile file) {
        return file instanceof CsvFile;
    }

    @Override
    public void updateView() {
        updateSurveySeries();
        updateBaseStations();
        validateInput();
    }

    private void updateSurveySeries() {
        surveySeriesSelector.getItems().clear();
        if (model.getChart(selectedFile) instanceof SensorLineChart chart) {
            surveySeriesSelector.getItems().addAll(chart.getSeriesNames());
            String selectedSeries = chart.getSelectedSeriesName();
            if (selectedSeries != null) {
                surveySeriesSelector.setValue(selectedSeries);
            } else if (!surveySeriesSelector.getItems().isEmpty()) {
                surveySeriesSelector.setValue(surveySeriesSelector.getItems().getFirst());
            }
        }
    }

    private void updateBaseStations() {
        SensorLineChart selectedBaseStation = baseStationSelector.getValue();
        baseStationSelector.getItems().setAll(model.getSensorCharts().stream()
                .filter(chart -> chart.getFile() != selectedFile)
                .toList());
        if (selectedBaseStation != null && baseStationSelector.getItems().contains(selectedBaseStation)) {
            baseStationSelector.setValue(selectedBaseStation);
        } else if (!baseStationSelector.getItems().isEmpty()) {
            baseStationSelector.setValue(baseStationSelector.getItems().getFirst());
        }
        updateBaseStationSeries();
    }

    private void updateBaseStationSeries() {
        String selectedSeries = baseStationSeriesSelector.getValue();
        baseStationSeriesSelector.getItems().clear();
        SensorLineChart baseStation = synchronizedBaseSeries.isSelected()
                ? selectedSurveyChart()
                : baseStationSelector.getValue();
        if (baseStation != null) {
            baseStationSeriesSelector.getItems().addAll(baseStation.getSeriesNames());
        }
        if (selectedSeries != null && baseStationSeriesSelector.getItems().contains(selectedSeries)) {
            baseStationSeriesSelector.setValue(selectedSeries);
        } else if (!baseStationSeriesSelector.getItems().isEmpty()) {
            baseStationSeriesSelector.setValue(baseStationSeriesSelector.getItems().getFirst());
        }
        baseStationSelector.setDisable(synchronizedBaseSeries.isSelected());
        validateInput();
    }

    private SensorLineChart selectedSurveyChart() {
        return model.getChart(selectedFile) instanceof SensorLineChart chart ? chart : null;
    }

    private void validateInput() {
        Double referenceField = parseReferenceField();
        boolean invalidReference = !referenceFieldInput.getText().isBlank() && referenceField == null;
        boolean invalid = surveySeriesSelector.getValue() == null
                || (!synchronizedBaseSeries.isSelected() && baseStationSelector.getValue() == null)
                || baseStationSeriesSelector.getValue() == null
                || outputSeriesInput.getText().isBlank()
                || outputSeriesInput.getText().equals(surveySeriesSelector.getValue())
                || invalidReference;
        disableActions(invalid);
    }

    private Double parseReferenceField() {
        String text = referenceFieldInput.getText().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            double value = Double.parseDouble(text);
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    protected void onApply(ActionEvent event) {
        if (!(model.getChart(selectedFile) instanceof SensorLineChart surveyChart)) {
            return;
        }
        SensorLineChart baseStationChart = baseStationSelector.getValue();
        String surveySeries = surveySeriesSelector.getValue();
        String baseStationSeries = baseStationSeriesSelector.getValue();
        String outputSeries = outputSeriesInput.getText().trim();
        Double referenceField = parseReferenceField();
        boolean useSynchronizedBaseSeries = synchronizedBaseSeries.isSelected();
        if ((!useSynchronizedBaseSeries && baseStationChart == null) || surveySeries == null || baseStationSeries == null) {
            return;
        }

        submitAction(() -> {
            try {
                DiurnalCorrectionResult result = useSynchronizedBaseSeries
                        ? correctionService.correctSynchronized(surveyChart.getFile().getGeoData(), surveySeries,
                                baseStationSeries, referenceField)
                        : correctionService.correct(surveyChart.getFile().getGeoData(), surveySeries,
                                baseStationChart.getFile().getGeoData(), baseStationSeries, referenceField);
                surveyChart.createDerivedSeries(outputSeries, surveySeries, result.values());
                processingWorkflow.recordDiurnalCorrection(surveyChart.getFile(),
                        new DiurnalCorrectionOptions(surveySeries, outputSeries, baseStationSeries,
                                useSynchronizedBaseSeries, result.referenceField()));
            } catch (IllegalArgumentException e) {
                Platform.runLater(() -> Dialogs.showError("Diurnal correction", e.getMessage()));
            }
            return null;
        });
    }

    @EventListener
    private void onFileSelected(FileSelectedEvent event) {
        Platform.runLater(() -> selectFile(event.getFile()));
    }

    @EventListener
    private void onFileOpened(FileOpenedEvent event) {
        Platform.runLater(this::updateBaseStations);
    }

    private static class BaseStationConverter extends StringConverter<SensorLineChart> {

        @Override
        public String toString(SensorLineChart chart) {
            if (chart == null) {
                return "";
            }
            File file = chart.getFile().getFile();
            return file != null ? file.getName() : "Unsaved base station";
        }

        @Override
        public SensorLineChart fromString(String value) {
            return null;
        }
    }
}
