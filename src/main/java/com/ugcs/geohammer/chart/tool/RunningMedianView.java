package com.ugcs.geohammer.chart.tool;

import com.ugcs.geohammer.PrefSettings;
import com.ugcs.geohammer.chart.csv.SensorLineChart;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.event.WhatChanged;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.util.Templates;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TextField;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;

@Component
public class RunningMedianView extends FilterToolView {

    private final Model model;

    private final PrefSettings preferences;

    private final TextField windowInput;

    public RunningMedianView(Model model, PrefSettings preferences, ExecutorService executor) {
        super(executor);

        this.model = model;
        this.preferences = preferences;

        windowInput = new TextField();
        windowInput.setPromptText("Enter window size");
        windowInput.textProperty().addListener(this::onShiftChange);

        inputContainer.getChildren().setAll(windowInput);

        showApply(true);
        showApplyToAll(true);
    }

    private void onShiftChange(ObservableValue<? extends String> observable, String oldValue, String newValue) {
        Integer window = null;
        if (newValue != null) {
            try {
                window = Integer.parseInt(newValue);
            } catch (NumberFormatException e) {
                // null
            }
        }
        windowInput.setUserData(window);
        validateInput();
    }

    public void validateInput() {
        Integer window = (Integer) windowInput.getUserData();
        boolean disable = window == null || window <= 0;
        disableActions(disable);
    }

    @Override
    public void loadPreferences() {
        String templateName = Templates.getTemplateName(selectedFile);
        if (!Strings.isNullOrEmpty(templateName)) {
            Nulls.ifPresent(preferences.getSetting("median_correction", templateName),
                    windowInput::setText);
        }
    }

    @Override
    public void savePreferences() {
        String templateName = Templates.getTemplateName(selectedFile);
        if (!Strings.isNullOrEmpty(templateName)) {
            preferences.saveSetting("median_correction", templateName, windowInput.getText());
        }
    }

    @Override
    public void apply() {
        Integer window = (Integer) windowInput.getUserData();
        if (window == null) {
            return;
        }
        if (model.getChart(selectedFile) instanceof SensorLineChart sensorChart) {
            // TODO
            //var rangeBefore = getSelectedSeriesRange();
            sensorChart.applyRunningMedian(sensorChart.getSelectedSeriesName(), window);
            //Platform.runLater(() -> updateGriddingMinMaxPreserveUserRange(rangeBefore));
            //showGridInputDataChangedWarning(true);
        }
    }

    @Override
    public void applyToAll() {
        Integer window = (Integer) windowInput.getUserData();
        if (window == null) {
            return;
        }
        if (model.getChart(selectedFile) instanceof SensorLineChart sensorChart) {
            // TODO
            //var rangeBefore = getSelectedSeriesRange();
            String seriesName = sensorChart.getSelectedSeriesName();
            model.getSensorCharts().stream()
                    .filter(c -> Templates.equals(c.getFile(), selectedFile))
                    .forEach(c -> c.applyRunningMedian(seriesName, window));
            //Platform.runLater(() -> updateGriddingMinMaxPreserveUserRange(rangeBefore));
            //showGridInputDataChangedWarning(true);
        }
    }
}
