package com.ugcs.geohammer.chart.tool.projection.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import org.springframework.stereotype.Component;

@Component
public class GridOptions {

    private final BooleanProperty autoUpdate = new SimpleBooleanProperty(true);

    private final DoubleProperty resolution = new SimpleDoubleProperty(0.5);

    private final BooleanProperty migration = new SimpleBooleanProperty(true);

    private final BooleanProperty refraction = new SimpleBooleanProperty(true);

    private final DoubleProperty fresnelApertureFactor = new SimpleDoubleProperty(2);

    private final BooleanProperty interpolateGrid = new SimpleBooleanProperty(false);

    private final BooleanProperty cropAir = new SimpleBooleanProperty(true);

    private final DoubleProperty gridProgress = new SimpleDoubleProperty(0);

    public boolean isAutoUpdate() {
        return autoUpdate.get();
    }

    public BooleanProperty autoUpdateProperty() {
        return autoUpdate;
    }

    public double getResolution() {
        return resolution.get();
    }

    public DoubleProperty resolutionProperty() {
        return resolution;
    }

    public boolean isMigration() {
        return migration.get();
    }

    public BooleanProperty migrationProperty() {
        return migration;
    }

    public boolean isRefraction() {
        return refraction.get();
    }

    public BooleanProperty refractionProperty() {
        return refraction;
    }

    public double getFresnelApertureFactor() {
        return fresnelApertureFactor.get();
    }

    public DoubleProperty fresnelApertureFactorProperty() {
        return fresnelApertureFactor;
    }

    public boolean isInterpolateGrid() {
        return interpolateGrid.get();
    }

    public BooleanProperty interpolateGridProperty() {
        return interpolateGrid;
    }

    public boolean isCropAir() {
        return cropAir.get();
    }

    public BooleanProperty cropAirProperty() {
        return cropAir;
    }

    public double getGridProgress() {
        return gridProgress.get();
    }

    public DoubleProperty gridProgressProperty() {
        return gridProgress;
    }
}
