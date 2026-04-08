package com.ugcs.geohammer.chart.tool.projection.model;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import org.springframework.stereotype.Component;

@Component
public class ProjectionOptions {

    public static final double MIN_RESAMPLING_STEP = 0.005;

    // pulse delay offset
    private final IntegerProperty sampleOffset = new SimpleIntegerProperty(0);

    private final DoubleProperty centerFrequency = new SimpleDoubleProperty(150);

    private final DoubleProperty relativePermittivity = new SimpleDoubleProperty(5.0);

    private final DoubleProperty antennaOffset = new SimpleDoubleProperty(-0.15);

    private final DoubleProperty terrainOffset = new SimpleDoubleProperty(0.45);

    private final DoubleProperty antennaSmoothingRadius = new SimpleDoubleProperty(0);

    private final DoubleProperty terrainSmoothingRadius = new SimpleDoubleProperty(2.5);

    private final DoubleProperty normalWeight = new SimpleDoubleProperty(0.5);

    public int getSampleOffset() {
        return sampleOffset.get();
    }

    public IntegerProperty sampleOffsetProperty() {
        return sampleOffset;
    }

    public double getCenterFrequency() {
        return centerFrequency.get();
    }

    public DoubleProperty centerFrequencyProperty() {
        return centerFrequency;
    }

    public double getRelativePermittivity() {
        return relativePermittivity.get();
    }

    public DoubleProperty relativePermittivityProperty() {
        return relativePermittivity;
    }

    public double getAntennaOffset() {
        return antennaOffset.get();
    }

    public DoubleProperty antennaOffsetProperty() {
        return antennaOffset;
    }

    public double getTerrainOffset() {
        return terrainOffset.get();
    }

    public DoubleProperty terrainOffsetProperty() {
        return terrainOffset;
    }

    public double getAntennaSmoothingRadius() {
        return antennaSmoothingRadius.get();
    }

    public DoubleProperty antennaSmoothingRadiusProperty() {
        return antennaSmoothingRadius;
    }

    public double getTerrainSmoothingRadius() {
        return terrainSmoothingRadius.get();
    }

    public DoubleProperty terrainSmoothingRadiusProperty() {
        return terrainSmoothingRadius;
    }

    public double getNormalWeight() {
        return normalWeight.get();
    }

    public DoubleProperty normalWeightProperty() {
        return normalWeight;
    }
}
