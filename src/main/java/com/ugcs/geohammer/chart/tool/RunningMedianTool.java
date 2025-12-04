package com.ugcs.geohammer.chart.tool;

import com.ugcs.geohammer.PrefSettings;
import com.ugcs.geohammer.chart.csv.SensorLineChart;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
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
public class RunningMedianTool extends FilterToolView {

    private final Model model;

    private final PrefSettings preferences;

    private final TextField windowInput;

    public RunningMedianTool(Model model, PrefSettings preferences, ExecutorService executor) {
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

    @Override
    public boolean isVisibleFor(SgyFile file) {
        return file instanceof CsvFile;
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
            windowInput.setText(preferences.getStringOrDefault(
                    "median_correction", templateName, Strings.empty()));
        }
    }

    @Override
    public void savePreferences() {
        String templateName = Templates.getTemplateName(selectedFile);
        if (!Strings.isNullOrEmpty(templateName)) {
            preferences.setValue("median_correction", templateName, windowInput.getText());
        }
    }

    @Override
    protected void onApply(ActionEvent event) {
        Integer window = (Integer) windowInput.getUserData();
        if (window == null) {
            return;
        }
        if (model.getChart(selectedFile) instanceof SensorLineChart sensorChart) {
            submitAction(() -> {
                sensorChart.applyRunningMedian(sensorChart.getSelectedSeriesName(), window);
                return null;
            });
        }
    }

    @Override
    protected void onApplyToAll(ActionEvent event) {
        Integer window = (Integer) windowInput.getUserData();
        if (window == null) {
            return;
        }
        if (model.getChart(selectedFile) instanceof SensorLineChart sensorChart) {
            String seriesName = sensorChart.getSelectedSeriesName();
            submitAction(() -> {
                model.getSensorCharts().stream()
                        .filter(c -> Templates.equals(c.getFile(), selectedFile))
                        .forEach(c -> c.applyRunningMedian(seriesName, window));
                return null;
            });
        }
    }

    @EventListener
    private void onFileSelected(FileSelectedEvent event) {
        Platform.runLater(() -> selectFile(event.getFile()));
    }
}
