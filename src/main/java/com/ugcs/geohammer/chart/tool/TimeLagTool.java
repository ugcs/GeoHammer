package com.ugcs.geohammer.chart.tool;

import com.ugcs.geohammer.PrefSettings;
import com.ugcs.geohammer.chart.csv.SensorLineChart;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
import com.ugcs.geohammer.model.event.WhatChanged;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.util.Templates;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.scene.control.TextField;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;

@Component
public class TimeLagTool extends FilterToolView {

    private final Model model;

    private final PrefSettings preferences;

    private final TextField shiftInput;

    public TimeLagTool(Model model, PrefSettings preferences, ExecutorService executor) {
        super(executor);

        this.model = model;
        this.preferences = preferences;

        shiftInput = new TextField();
        shiftInput.setPromptText("Enter time-lag (fiducials)");
        shiftInput.textProperty().addListener(this::onShiftChange);

        inputContainer.getChildren().setAll(shiftInput);

        showApply(true);
        showApplyToAll(true);
    }

    @Override
    public boolean isVisibleFor(SgyFile file) {
        return file instanceof CsvFile;
    }

    private void onShiftChange(ObservableValue<? extends String> observable, String oldValue, String newValue) {
        Integer shift = null;
        if (newValue != null) {
            try {
                shift = Integer.parseInt(newValue);
            } catch (NumberFormatException e) {
                // null
            }
        }
        shiftInput.setUserData(shift);
        validateInput();
    }

    public void validateInput() {
        Integer shift = (Integer) shiftInput.getUserData();
        boolean disable = shift == null || Math.abs(shift) > 10000;
        disableActions(disable);
    }

    @Override
    public void loadPreferences() {
        String templateName = Templates.getTemplateName(selectedFile);
        if (!Strings.isNullOrEmpty(templateName)) {
            Nulls.ifPresent(preferences.getSetting("timelag", templateName),
                    shiftInput::setText);
        }
    }

    @Override
    public void savePreferences() {
        String templateName = Templates.getTemplateName(selectedFile);
        if (!Strings.isNullOrEmpty(templateName)) {
            preferences.saveSetting("timelag", templateName, shiftInput.getText());
        }
    }

    @Override
    protected void onApply(ActionEvent event) {
        Integer shift = (Integer) shiftInput.getUserData();
        if (shift == null) {
            return;
        }
        if (model.getChart(selectedFile) instanceof SensorLineChart sensorChart) {
            submitAction(() -> {
                sensorChart.applyTimeLag(sensorChart.getSelectedSeriesName(), shift);
                model.publishEvent(new WhatChanged(this, WhatChanged.Change.csvDataFiltered));
                return null;
            });
        }
    }

    @Override
    protected void onApplyToAll(ActionEvent event) {
        Integer shift = (Integer) shiftInput.getUserData();
        if (shift == null) {
            return;
        }
        if (model.getChart(selectedFile) instanceof SensorLineChart sensorChart) {
            String seriesName = sensorChart.getSelectedSeriesName();
            submitAction(() -> {
                model.getSensorCharts().stream()
                        .filter(c -> Templates.equals(c.getFile(), selectedFile))
                        .forEach(c -> c.applyTimeLag(seriesName, shift));
                model.publishEvent(new WhatChanged(this, WhatChanged.Change.csvDataFiltered));
                return null;
            });
        }
    }

    @EventListener
    private void onFileSelected(FileSelectedEvent event) {
        Platform.runLater(() -> selectFile(event.getFile()));
    }
}
