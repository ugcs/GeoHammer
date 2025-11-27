package com.ugcs.geohammer.chart.tool;

import com.ugcs.geohammer.PrefSettings;
import com.ugcs.geohammer.chart.csv.SensorLineChart;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.format.svlog.SonarFile;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
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
public class LowPassTool extends FilterToolView {

    private final Model model;

    private final PrefSettings preferences;

    private final TextField orderInput;

    public LowPassTool(Model model, PrefSettings preferences, ExecutorService executor) {
        super(executor);

        this.model = model;
        this.preferences = preferences;

        orderInput = new TextField();
        orderInput.setPromptText("Enter cutoff wavelength (fiducials)");
        orderInput.textProperty().addListener(this::onOrderChange);

        inputContainer.getChildren().setAll(orderInput);

        showApply(true);
        showApplyToAll(true);
    }

    @Override
    public boolean isVisibleFor(SgyFile file) {
        return file instanceof CsvFile || file instanceof SonarFile;
    }

    private void onOrderChange(ObservableValue<? extends String> observable, String oldValue, String newValue) {
        Integer order = null;
        if (newValue != null) {
            try {
                order = Integer.parseInt(newValue);
            } catch (NumberFormatException e) {
                // null
            }
        }
        orderInput.setUserData(order);
        validateInput();
    }

    public void validateInput() {
        Integer order = (Integer) orderInput.getUserData();
        boolean disable = order == null || order < 0 || order > 10000;
        disableActions(disable);
    }

    @Override
    public void loadPreferences() {
        String templateName = Templates.getTemplateName(selectedFile);
        if (!Strings.isNullOrEmpty(templateName)) {
            Nulls.ifPresent(preferences.getSetting("lowpass", templateName),
                    orderInput::setText);
        }
    }

    @Override
    public void savePreferences() {
        String templateName = Templates.getTemplateName(selectedFile);
        if (!Strings.isNullOrEmpty(templateName)) {
            preferences.saveSetting("lowpass", templateName, orderInput.getText());
        }
    }

    @Override
    protected void onApply(ActionEvent event) {
        Integer order = (Integer)orderInput.getUserData();
        if (order == null) {
            return;
        }
        if (model.getChart(selectedFile) instanceof SensorLineChart sensorChart) {
            submitAction(() -> {
                sensorChart.applyLowPass(sensorChart.getSelectedSeriesName(), order);
                // TODO
                //showGridInputDataChangedWarning(true);
                return null;
            });
        }
    }

    @Override
    protected void onApplyToAll(ActionEvent event) {
        Integer order = (Integer)orderInput.getUserData();
        if (order == null) {
            return;
        }
        if (model.getChart(selectedFile) instanceof SensorLineChart sensorChart) {
            String seriesName = sensorChart.getSelectedSeriesName();
            submitAction(() -> {
                model.getSensorCharts().stream()
                        .filter(c -> Templates.equals(c.getFile(), selectedFile))
                        .forEach(c -> c.applyLowPass(seriesName, order));
                // TODO
                //showGridInputDataChangedWarning(true);
                return null;
            });
        }
    }

    @EventListener
    private void onFileSelected(FileSelectedEvent event) {
        Platform.runLater(() -> selectFile(event.getFile()));
    }
}
