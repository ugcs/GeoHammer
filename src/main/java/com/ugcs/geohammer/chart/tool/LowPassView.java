package com.ugcs.geohammer.chart.tool;

import com.ugcs.geohammer.PrefSettings;
import com.ugcs.geohammer.chart.csv.SensorLineChart;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.util.Templates;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TextField;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;

@Component
public class LowPassView extends FilterToolView {

    private final Model model;

    private final PrefSettings preferences;

    private final TextField orderInput;

    public LowPassView(Model model, PrefSettings preferences, ExecutorService executor) {
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
    public void apply() {
        Integer order = (Integer)orderInput.getUserData();
        if (order == null) {
            return;
        }
        if (model.getChart(selectedFile) instanceof SensorLineChart sensorChart) {
            sensorChart.applyLowPass(sensorChart.getSelectedSeriesName(), order);
            //showGridInputDataChangedWarning(true);
        }
    }

    @Override
    public void applyToAll() {
        Integer order = (Integer)orderInput.getUserData();
        if (order == null) {
            return;
        }
        if (model.getChart(selectedFile) instanceof SensorLineChart sensorChart) {
            String seriesName = sensorChart.getSelectedSeriesName();
            model.getSensorCharts().stream()
                    .filter(c -> Templates.equals(c.getFile(), selectedFile))
                    .forEach(c -> c.applyLowPass(seriesName, order));
            //showGridInputDataChangedWarning(true);
        }
    }
}
