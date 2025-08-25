package com.ugcs.gprvisualizer.app;

import com.github.thecoldwine.sigrun.common.ext.CsvFile;
import com.github.thecoldwine.sigrun.common.ext.LatLon;
import com.ugcs.gprvisualizer.app.parcers.GeoData;
import com.ugcs.gprvisualizer.app.service.DistanceConverterService;
import javafx.scene.Node;
import javafx.scene.chart.ValueAxis;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class SensorLineChartXAxis extends ValueAxis<Number> {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SensorLineChartXAxis.class);

    private final DecimalFormat formatter = new DecimalFormat();
    private final int numTicks;
    private DistanceConverterService.Unit distanceUnit = DistanceConverterService.Unit.getDefault();
    private final CsvFile file;
    private List<Double> cumulativeDistances = new ArrayList<>();

    public SensorLineChartXAxis(int numTicks, CsvFile file) {
        this.numTicks = numTicks;
        this.file = file;
    }

    public void setDistanceUnit(DistanceConverterService.Unit unit) {
        this.distanceUnit = unit;
        requestAxisLayout();
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
        double distance = getDistanceAtTrace(traceIndex);

        // Format distance based on unit
        return formatter.format(distance);
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

    private double getDistanceAtTrace(int traceIndex) {
        if (cumulativeDistances == null) {
            initializeCumulativeDistances();
        }

        List<GeoData> geoData = file.getGeoData();
        if (geoData.isEmpty() || traceIndex < 0 || traceIndex >= geoData.size()) {
            return 0.0;
        }

        return cumulativeDistances.get(traceIndex);
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
        AxisRange r = (AxisRange) range;
        setLowerBound(r.lowerBound());
        setUpperBound(r.upperBound());
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
}