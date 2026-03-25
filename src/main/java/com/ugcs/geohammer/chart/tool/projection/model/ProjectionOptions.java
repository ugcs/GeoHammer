package com.ugcs.geohammer.chart.tool.projection.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import org.springframework.stereotype.Component;

@Component
public class ProjectionOptions {

    private final DoubleProperty relativePermittivity = new SimpleDoubleProperty(5.0);

    // pulse delay offset
    private final IntegerProperty sampleOffset = new SimpleIntegerProperty(0);

    private final DoubleProperty antennaOffset = new SimpleDoubleProperty(-0.15);

    private final DoubleProperty terrainOffset = new SimpleDoubleProperty(0);

    private final DoubleProperty antennaSmoothingRadius = new SimpleDoubleProperty(0);

    private final DoubleProperty terrainSmoothingRadius = new SimpleDoubleProperty(2.5);

    private final BooleanProperty diffuseNormals = new SimpleBooleanProperty(false);

    public double getRelativePermittivity() {
        return relativePermittivity.get();
    }

    public DoubleProperty relativePermittivityProperty() {
        return relativePermittivity;
    }

    public int getSampleOffset() {
        return sampleOffset.get();
    }

    public IntegerProperty sampleOffsetProperty() {
        return sampleOffset;
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

    public boolean isDiffuseNormals() {
        return diffuseNormals.get();
    }

    public BooleanProperty diffuseNormalsProperty() {
        return diffuseNormals;
    }
}
