package com.ugcs.gprvisualizer.app.axis;

import com.github.thecoldwine.sigrun.common.ext.CsvFile;
import com.github.thecoldwine.sigrun.common.ext.LatLon;
import com.ugcs.gprvisualizer.app.TraceUnit;
import com.ugcs.gprvisualizer.app.parcers.GeoData;
import com.ugcs.gprvisualizer.app.service.TemplateSettingsModel;
import com.ugcs.gprvisualizer.event.TemplateUnitChangedEvent;
import com.ugcs.gprvisualizer.event.WhatChanged;
import com.ugcs.gprvisualizer.gpr.Model;
import com.ugcs.gprvisualizer.utils.FileTemplate;
import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.chart.ValueAxis;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Component
public class SensorLineChartXAxis extends ValueAxis<Number> {

    private static final Logger log = LoggerFactory.getLogger(SensorLineChartXAxis.class);

    private static final DateTimeFormatter TIME_FORMATTER_SECONDS = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter TIME_FORMATTER_MILLISECONDS = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final Model model;
    private final TemplateSettingsModel templateSettingsModel;
    private final DecimalFormat formatter = new DecimalFormat();
    private final CsvFile file;
    private final int numTicks;
    @Nonnull
    private final List<Double> cumulativeDistances = new ArrayList<>();
    @Nullable
    private Button labelButton = null;

    public SensorLineChartXAxis(Model model, TemplateSettingsModel templateSettingsModel, CsvFile file, int numTicks) {
        this.model = model;
        this.templateSettingsModel = templateSettingsModel;
        this.file = file;
        this.numTicks = numTicks;
        setLabel(getUnit().getLabel());
    }

    public void setUnit(TraceUnit traceUnit) {
        if (traceUnit == null ) {
            return;
        }

        String templateName = FileTemplate.getTemplateName(model, file.getFile());
        templateSettingsModel.setUnitForTemplate(templateName, traceUnit);

        setLabel(traceUnit.getLabel());

        if (labelButton != null) {
            labelButton.setText(traceUnit.getLabel());
            labelButton.applyCss();
            labelButton.autosize();
        }

        invalidateRange();
    }

    public TraceUnit getUnit() {
        if (file == null || file.getFile() == null) {
            return TraceUnit.getDefault();
        }

        String templateName = FileTemplate.getTemplateName(model, file.getFile());
        if (templateName == null) {
            return TraceUnit.getDefault();
        }

        if (!templateSettingsModel.hasUnitForTemplate(templateName)) {
            return TraceUnit.getDefault();
        } else {
            return templateSettingsModel.getUnitForTemplate(templateName);
        }
    }

    @Override
    protected String getTickMarkLabel(Number value) {
        if (file == null || file.getGeoData().isEmpty()) {
            log.warn("No geo data available in the file.");
            return "";
        }

        int traceIndex = value.intValue();
        TraceUnit traceUnit = getUnit();
        switch (traceUnit) {
            case METERS, KILOMETERS, MILES, FEET -> {
                double distance = getDistanceAtTrace(traceIndex, traceUnit);
                return formatter.format(distance);
            }
            case TIME -> {
                LocalDateTime dt = file.getGeoData().get(traceIndex).getDateTime();
                if (dt == null) {
                    return "";
                }

                double visibleRange = getUpperBound() - getLowerBound();
                boolean showMilliseconds = visibleRange < (double) file.numTraces() / numTicks;

                DateTimeFormatter formatter = showMilliseconds ?
                        TIME_FORMATTER_MILLISECONDS : TIME_FORMATTER_SECONDS;
                return dt.format(formatter);
            }
            case TRACES -> {
                return String.format("%1$3s", traceIndex);
            }
            default -> {
                return "";
            }
        }
    }

    private void initializeCumulativeDistances() {
        List<GeoData> geoData = file.getGeoData();
        cumulativeDistances.clear();

        if (geoData.isEmpty()) {
            return;
        }

        cumulativeDistances.add(0.0);

        for (int i = 1; i < geoData.size(); i++) {
            LatLon previousLocation = geoData.get(i - 1).getLatLon();
            LatLon currentLocation = geoData.get(i).getLatLon();

            Optional<Integer> previousLineIndex = geoData.get(i - 1).getLineIndex();
            Optional<Integer> currentLineIndex = geoData.get(i).getLineIndex();

            // For different lines, reset distance to 0
            if (previousLineIndex.isPresent() && currentLineIndex.isPresent()) {
                if (!previousLineIndex.get().equals(currentLineIndex.get())) {
                    cumulativeDistances.add(0.0);
                    continue;
                }
            }

            double previousDistance = cumulativeDistances.get(i - 1);
            double segmentDistance = previousLocation.getDistance(currentLocation);
            cumulativeDistances.add(previousDistance + segmentDistance);
        }
    }

    private double getDistanceAtTrace(int traceIndex, TraceUnit distanceTraceUnit) {
        List<GeoData> geoData = file.getGeoData();
        if (cumulativeDistances.size() != geoData.size()) {
            initializeCumulativeDistances();
        }

        if (geoData.isEmpty() || traceIndex < 0 || traceIndex >= geoData.size()) {
            return 0.0;
        }
        return TraceUnit.convert(cumulativeDistances.get(traceIndex), distanceTraceUnit);
    }

    @Override
    protected List<Number> calculateTickValues(double length, Object range) {
        AxisRange r = (AxisRange) range;
        double lower = r.lowerBound();
        double upper = r.upperBound();

        List<Number> tickValues = new ArrayList<>();

        if (upper <= lower) {
            return tickValues;
        }

        double step = (upper - lower) / numTicks;

        for (int i = 0; i <= numTicks; i++) {
            double tickValue = lower + i * step;
            tickValues.add(tickValue);
        }

        return tickValues;
    }

    @EventListener
    public void onGeoDataChanged(WhatChanged event) {
        if (event.isTraceCut()) {
            cumulativeDistances.clear();
            Platform.runLater(this::requestAxisLayout);
        }
    }

    @Override
    protected Object getRange() {
        return new AxisRange(getLowerBound(), getUpperBound());
    }

    @Override
    protected void setRange(Object range, boolean animate) {
        AxisRange axisRange = (AxisRange) range;
        setLowerBound(axisRange.lowerBound());
        setUpperBound(axisRange.upperBound());
    }

    @Override
    protected Object autoRange(double minValue, double maxValue, double length, double labelSize) {
        return new AxisRange(minValue, maxValue);
    }

    @Override
    protected List<Number> calculateMinorTickMarks() {
        return List.of();
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();

        Label axisLabel = (Label) lookup(".axis-label");
        if (axisLabel != null && !axisLabel.getStyleClass().contains("clickable-label") && labelButton == null) {
            axisLabel.setVisible(false);

            labelButton = new Button(axisLabel.getText());
            labelButton.getStyleClass().add("clickable-label");
            labelButton.setCursor(Cursor.HAND);
            labelButton.setVisible(this.isVisible() && this.isTickLabelsVisible());

            labelButton.setOnAction(event -> handleLabelClick());

            getChildren().add(labelButton);
        } else if (labelButton != null) {
            labelButton.setVisible(this.isVisible() && this.isTickLabelsVisible());
            getChildren().remove(labelButton);
            labelButton.setText(getUnitLabel(getUnit()));
            labelButton.autosize();

            labelButton.setLayoutX((getWidth() - labelButton.getWidth()) / 2);
            labelButton.setLayoutY(getHeight() - labelButton.getHeight() + 5);
            getChildren().add(labelButton);
        }
    }

    // Change "traces" to "measurements" for better clarity
    private String getUnitLabel(TraceUnit unit) {
        if (unit == TraceUnit.TRACES) {
            return "measurements";
        }
        return unit.getLabel();
    }

    private void handleLabelClick() {
        TraceUnit currentTraceUnit = getUnit();
        TraceUnit[] traceUnits = TraceUnit.values();

        int nextIndex = (currentTraceUnit.ordinal() + 1) % traceUnits.length;
        TraceUnit nextTraceUnit = traceUnits[nextIndex];
        setUnit(nextTraceUnit);

        String templateName = FileTemplate.getTemplateName(model, file.getFile());
        notifyTemplateUnitChange(templateName, nextTraceUnit);
    }

    private void notifyTemplateUnitChange(String templateName, TraceUnit newTraceUnit) {
        Platform.runLater(() ->
                model.publishEvent(new TemplateUnitChangedEvent(this, file, templateName, newTraceUnit))
        );
    }
}