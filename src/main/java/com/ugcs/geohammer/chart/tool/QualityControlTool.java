package com.ugcs.geohammer.chart.tool;

import com.ugcs.geohammer.PrefSettings;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.map.layer.QualityLayer;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
import com.ugcs.geohammer.model.event.WhatChanged;
import com.ugcs.geohammer.service.quality.AltitudeCheck;
import com.ugcs.geohammer.service.quality.DataCheck;
import com.ugcs.geohammer.service.quality.LineDistanceCheck;
import com.ugcs.geohammer.service.quality.QualityCheck;
import com.ugcs.geohammer.service.quality.QualityControl;
import com.ugcs.geohammer.service.quality.QualityIssue;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.util.Templates;
import com.ugcs.geohammer.util.Text;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

@Component
public class QualityControlTool extends FilterToolView {

    public static double DEFAULT_MAX_LINE_DISTANCE = 1.0;

    public static double DEFAULT_LINE_DISTANCE_TOLERANCE = 0.2;

    public static double DEFAULT_MAX_ALTITUDE = 1.0;

    public static double DEFAULT_ALTITUDE_TOLERANCE = 0.2;

    private final Model model;

    private final PrefSettings preferences;

    private final QualityLayer qualityLayer;

    // view

    private final TextField maxLineDistanceInput;

    private final TextField lineDistanceToleranceInput;

    private final TextField maxAltitudeInput;

    private final TextField altitudeToleranceInput;

    public QualityControlTool(
            Model model,
            PrefSettings preferences,
            ExecutorService executor,
            QualityLayer qualityLayer
    ) {
        super(executor);

        this.model = model;
        this.preferences = preferences;
        this.qualityLayer = qualityLayer;

        Label lineDistanceLabel = new Label("Distance between lines");

        maxLineDistanceInput = new TextField(String.valueOf(DEFAULT_MAX_LINE_DISTANCE));
        maxLineDistanceInput.setPromptText("Distance between lines (m)");
        maxLineDistanceInput.textProperty().addListener(this::onInputChange);

        lineDistanceToleranceInput = new TextField(String.valueOf(DEFAULT_LINE_DISTANCE_TOLERANCE));
        lineDistanceToleranceInput.setPromptText("Distance tolerance (m)");
        lineDistanceToleranceInput.textProperty().addListener(this::onInputChange);

        Label altitudeLabel = new Label("Altitude AGL");

        maxAltitudeInput = new TextField(String.valueOf(DEFAULT_MAX_ALTITUDE));
        maxAltitudeInput.setPromptText("Altitude AGL (m)");
        maxAltitudeInput.textProperty().addListener(this::onInputChange);

        altitudeToleranceInput = new TextField(String.valueOf(DEFAULT_ALTITUDE_TOLERANCE));
        altitudeToleranceInput.setPromptText("Altitude tolerance (m)");
        altitudeToleranceInput.textProperty().addListener(this::onInputChange);

        inputContainer.getChildren().setAll(
                lineDistanceLabel,
                maxLineDistanceInput,
                lineDistanceToleranceInput,
                altitudeLabel,
                maxAltitudeInput,
                altitudeToleranceInput
        );

        showApply(true);
        showApplyToAll(true);

        validateInput();
    }

    @Override
    public boolean isVisibleFor(SgyFile file) {
        return file instanceof CsvFile;
    }

    private void onInputChange(ObservableValue<? extends String> observable, String oldValue, String newValue) {
        validateInput();
    }

    public void validateInput() {
        disableActions(false); // always enabled
    }

    @Override
    public void show(boolean show) {
        super.show(show);

        if (qualityLayer != null && qualityLayer.isActive() != show) {
            qualityLayer.setActive(show);
            model.publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
        }
    }

    @Override
    public void loadPreferences() {
        String templateName = Templates.getTemplateName(selectedFile);
        if (!Strings.isNullOrEmpty(templateName)) {
            Nulls.ifPresent(preferences.getSetting("quality_max_line_distance", templateName),
                    maxLineDistanceInput::setText);
            Nulls.ifPresent(preferences.getSetting("quality_line_distance_tolerance", templateName),
                    lineDistanceToleranceInput::setText);
            Nulls.ifPresent(preferences.getSetting("quality_max_altitude", templateName),
                    maxAltitudeInput::setText);
            Nulls.ifPresent(preferences.getSetting("quality_altitude_tolerance", templateName),
                    altitudeToleranceInput::setText);
        }
    }

    @Override
    public void savePreferences() {
        String templateName = Templates.getTemplateName(selectedFile);
        if (!Strings.isNullOrEmpty(templateName)) {
            preferences.saveSetting("quality_max_line_distance", templateName,
                    maxLineDistanceInput.getText());
            preferences.saveSetting("quality_line_distance_tolerance", templateName,
                    lineDistanceToleranceInput.getText());
            preferences.saveSetting("quality_max_altitude", templateName,
                    maxAltitudeInput.getText());
            preferences.saveSetting("quality_altitude_tolerance", templateName,
                    altitudeToleranceInput.getText());
        }
    }

    private QualityControlParams getParams() {
        Double maxLineDistance = Text.parseDouble(maxLineDistanceInput.getText());
        if (maxLineDistance != null && maxLineDistance <= 0.0) {
            maxLineDistance = null;
        }
        Double lineDistanceTolerance = Text.parseDouble(lineDistanceToleranceInput.getText());
        Double maxAltitude = Text.parseDouble(maxAltitudeInput.getText());
        if (maxAltitude != null && maxAltitude <= 0.0) {
            maxAltitude = null;
        }
        Double altitudeTolerance = Text.parseDouble(altitudeToleranceInput.getText());
        return new QualityControlParams(
                maxLineDistance,
                lineDistanceTolerance,
                maxAltitude,
                altitudeTolerance
        );
    }

    private List<QualityCheck> createQualityChecks(QualityControlParams params) {
        List<QualityCheck> checks = new ArrayList<>();
        if (params.maxLineDistance() != null && params.lineDistanceTolerance() != null) {
            checks.add(new LineDistanceCheck(
                    params.maxLineDistance() + params.lineDistanceTolerance()
            ));
        }
        double lineDistance = params.maxLineDistance() != null
                ? params.maxLineDistance()
                : QualityControlTool.DEFAULT_MAX_LINE_DISTANCE;
        checks.add(new DataCheck(
                0.35 * lineDistance
        ));
        if (params.maxAltitude() != null && params.altitudeTolerance() != null) {
            checks.add(new AltitudeCheck(
                    params.maxAltitude(),
                    params.altitudeTolerance(),
                    0.35 * lineDistance
            ));
        }
        return checks;
    }

    @Override
    protected void onApply(ActionEvent event) {
        if (selectedFile == null) {
            return;
        }

        submitAction(() -> {
            QualityControlParams params = getParams();
            List<QualityCheck> checks = createQualityChecks(params);

            QualityControl qualityControl = new QualityControl();
            List<QualityIssue> issues = qualityControl.getQualityIssues(List.of(selectedFile), checks);

            qualityLayer.setIssues(issues);
            model.publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
            return null;
        });
    }

    @Override
    protected void onApplyToAll(ActionEvent event) {
        if (selectedFile == null) {
            return;
        }

        submitAction(() -> {
            List<SgyFile> files = new ArrayList<>();
            for (SgyFile file : model.getFileManager().getFiles()) {
                if (Templates.equals(file, selectedFile)) {
                    files.add(file);
                }
            }

            QualityControlParams params = getParams();
            List<QualityCheck> checks = createQualityChecks(params);

            QualityControl qualityControl = new QualityControl();
            List<QualityIssue> issues = qualityControl.getQualityIssues(files, checks);

            qualityLayer.setIssues(issues);
            model.publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
            return null;
        });
    }

    @EventListener
    private void onFileSelected(FileSelectedEvent event) {
        Platform.runLater(() -> selectFile(event.getFile()));
    }

    private record QualityControlParams(
            Double maxLineDistance,
            Double lineDistanceTolerance,
            Double maxAltitude,
            Double altitudeTolerance
    ) {
    }
}
