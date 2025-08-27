package com.ugcs.gprvisualizer.app;

import com.github.thecoldwine.sigrun.common.ext.CsvFile;
import com.github.thecoldwine.sigrun.common.ext.LatLon;
import com.ugcs.gprvisualizer.app.parcers.GeoData;
import com.ugcs.gprvisualizer.app.service.DistanceConverterService;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.chart.ValueAxis;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class SensorLineChartXAxis extends ValueAxis<Number> {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SensorLineChartXAxis.class);

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private DistanceConverterService.Unit distanceUnit = DistanceConverterService.Unit.getDefault();
    private final DecimalFormat formatter = new DecimalFormat();
    private final int numTicks;
    private final CsvFile file;
    @Nonnull
    private List<Double> cumulativeDistances = new ArrayList<>();
    @Nullable
    private Button labelButton = null;

    public SensorLineChartXAxis(int numTicks, CsvFile file) {
        this.numTicks = numTicks;
        this.file = file;
        setLabel(distanceUnit.getLabel());
    }

    public void setDistanceUnit(DistanceConverterService.Unit unit) {
        if (unit == null || unit == this.distanceUnit) {
            return;
        }
        this.distanceUnit = unit;
        setLabel(unit.getLabel());

        if (labelButton != null) {
            labelButton.setText(unit.getLabel());
            labelButton.applyCss();
            labelButton.autosize();
        }

        invalidateRange();
    }

    public DistanceConverterService.Unit getDistanceUnit() {
        return distanceUnit;
    }

    @Override
    protected String getTickMarkLabel(Number value) {
        if (file == null || file.getGeoData().isEmpty()) {
            log.warn("No geo data available in the file.");
            return "";
        }

        int traceIndex = value.intValue();
        DistanceConverterService.Unit unit = distanceUnit;
        switch (unit) {
            case METERS, KILOMETERS, MILES, FEET -> {
                if (!unit.isDistanceBased()) {
                    log.warn("Selected unit {} is not distance-based.", unit);
                    return "";
                }
                double distance = getDistanceAtTrace(traceIndex, unit);
                return formatter.format(distance);
            }
            case SECONDS -> {
                LocalDateTime dt = file.getGeoData().get(traceIndex).getDateTime();
                if (dt == null) {
                    log.warn("DateTime is null for trace index {}.", traceIndex);
                    return "";
                }
                return dt.format(TIME_FORMATTER);
            }
            default -> {
                return "";
            }
        }
    }

    private void initializeCumulativeDistances() {
        List<GeoData> geoData = file.getGeoData();
        cumulativeDistances = new ArrayList<>(geoData.size());

        if (geoData.isEmpty()) {
            return;
        }

        cumulativeDistances.add(0.0);

        for (int i = 1; i < geoData.size(); i++) {
            LatLon previous = geoData.get(i - 1).getLatLon();
            LatLon current = geoData.get(i).getLatLon();

            double previousDistance = cumulativeDistances.get(i - 1);
            double segmentDistance = previous.getDistance(current);
            cumulativeDistances.add(previousDistance + segmentDistance);
        }
    }

    private double getDistanceAtTrace(int traceIndex, DistanceConverterService.Unit distanceUnit) {
        if (cumulativeDistances.isEmpty()) {
            initializeCumulativeDistances();
        }

        List<GeoData> geoData = file.getGeoData();
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
            labelButton.setText(getDistanceUnit().getLabel());
            labelButton.setPrefWidth(35.0);
            labelButton.autosize();

            labelButton.setLayoutX((getWidth() - labelButton.getWidth()) / 2);
            labelButton.setLayoutY(getHeight() - labelButton.getHeight() + 5);
            getChildren().add(labelButton);
        }
    }

    private void handleLabelClick() {
        DistanceConverterService.Unit currentUnit = getDistanceUnit();
        DistanceConverterService.Unit[] units = DistanceConverterService.Unit.values();

        int currentIndex = Arrays.asList(units).indexOf(currentUnit);
        int nextIndex = (currentIndex + 1) % units.length;

        setDistanceUnit(units[nextIndex]);
    }
}