package com.ugcs.gprvisualizer.app.axis;

import com.github.thecoldwine.sigrun.common.ext.CsvFile;
import com.github.thecoldwine.sigrun.common.ext.LatLon;
import com.ugcs.gprvisualizer.app.AxisRange;
import com.ugcs.gprvisualizer.app.parcers.GeoData;
import com.ugcs.gprvisualizer.app.service.DistanceConverterService;
import com.ugcs.gprvisualizer.event.WhatChanged;
import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.Node;
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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Component
public class SensorLineChartXAxis extends ValueAxis<Number> {

    private static final Logger log = LoggerFactory.getLogger(SensorLineChartXAxis.class);

    private static final DateTimeFormatter TIME_FORMATTER_SECONDS = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter TIME_FORMATTER_MILLISECONDS = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private DistanceConverterService.Unit unit = DistanceConverterService.Unit.getDefault();
    private final DecimalFormat formatter = new DecimalFormat();
    private final int numTicks;
    private final CsvFile file;
    @Nonnull
    private final List<Double> cumulativeDistances = new ArrayList<>();
    @Nullable
    private Button labelButton = null;

    public SensorLineChartXAxis(int numTicks, CsvFile file) {
        this.numTicks = numTicks;
        this.file = file;
        setLabel(unit.getLabel());
    }

    public void setUnit(DistanceConverterService.Unit unit) {
        if (unit == null || unit == this.unit) {
            return;
        }
        this.unit = unit;
        setLabel(unit.getLabel());

        if (labelButton != null) {
            labelButton.setText(unit.getLabel());
            labelButton.applyCss();
            labelButton.autosize();
        }

        invalidateRange();
    }

    public DistanceConverterService.Unit getUnit() {
        return unit;
    }

    @Override
    protected String getTickMarkLabel(Number value) {
        if (file == null || file.getGeoData().isEmpty()) {
            log.warn("No geo data available in the file.");
            return "";
        }

        int traceIndex = value.intValue();
        DistanceConverterService.Unit unit = this.unit;
        switch (unit) {
            case METERS, KILOMETERS, MILES, FEET -> {
                if (!unit.isDistanceBased()) {
                    log.warn("Selected unit {} is not distance-based.", unit);
                    return "";
                }
                double distance = getDistanceAtTrace(traceIndex, unit);
                return formatter.format(distance);
            }
            case TIME -> {
                LocalDateTime dt = file.getGeoData().get(traceIndex).getDateTime();
                if (dt == null) {
                    log.warn("DateTime is null for trace index {}.", traceIndex);
                    return "";
                }

                double visibleRange = getUpperBound() - getLowerBound();
                boolean showMilliseconds = visibleRange < (double) (file.numTraces()) / numTicks;

                DateTimeFormatter formatter = showMilliseconds ?
                        TIME_FORMATTER_MILLISECONDS : TIME_FORMATTER_SECONDS;
                return dt.format(formatter);
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

    private double getDistanceAtTrace(int traceIndex, DistanceConverterService.Unit distanceUnit) {
        List<GeoData> geoData = file.getGeoData();
        if (cumulativeDistances.size() != geoData.size()) {
            initializeCumulativeDistances();
        }

        if (geoData.isEmpty() || traceIndex < 0 || traceIndex >= geoData.size()) {
            return 0.0;
        }
        return DistanceConverterService.convert(cumulativeDistances.get(traceIndex), distanceUnit);
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
    public Node getStyleableNode() {
        return super.getStyleableNode();
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

            labelButton.setOnAction(event -> handleLabelClick());

            getChildren().add(labelButton);
        } else if (labelButton != null) {
            getChildren().remove(labelButton);
            labelButton.setText(getUnit().getLabel());
            labelButton.autosize();

            labelButton.setLayoutX((getWidth() - labelButton.getWidth()) / 2);
            labelButton.setLayoutY(getHeight() - labelButton.getHeight() + 5);
            getChildren().add(labelButton);
        }
    }

    private void handleLabelClick() {
        DistanceConverterService.Unit currentUnit = getUnit();
        List<DistanceConverterService.Unit> units = Arrays.stream(DistanceConverterService.Unit.values())
                .filter(u -> u != DistanceConverterService.Unit.TRACES)
                .toList();

        int currentIndex = units.indexOf(currentUnit);
        int nextIndex = (currentIndex + 1) % units.size();

        setUnit(units.get(nextIndex));
    }
}