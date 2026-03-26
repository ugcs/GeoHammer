package com.ugcs.geohammer.chart.tool.projection.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.springframework.stereotype.Component;

@Component
public class GridOptions {

    public static final double DEFAULT_CELL_WIDTH = 0.05;

    public static final double DEFAULT_CELL_HEIGHT = 0.05;

    private final DoubleProperty cellWidth = new SimpleDoubleProperty(DEFAULT_CELL_WIDTH);

    private final DoubleProperty cellHeight = new SimpleDoubleProperty(DEFAULT_CELL_HEIGHT);

    private final ObjectProperty<GridSamplingMethod> samplingMethod = new SimpleObjectProperty<>(GridSamplingMethod.DEPTH_WEIGHTED);

    private final BooleanProperty interpolateGrid = new SimpleBooleanProperty(false);

    private final BooleanProperty cropAir = new SimpleBooleanProperty(true);

    public double getCellWidth() {
        return cellWidth.get();
    }

    public DoubleProperty cellWidthProperty() {
        return cellWidth;
    }

    public double getCellHeight() {
        return cellHeight.get();
    }

    public DoubleProperty cellHeightProperty() {
        return cellHeight;
    }

    public GridSamplingMethod getSamplingMethod() {
        return samplingMethod.get();
    }

    public ObjectProperty<GridSamplingMethod> samplingMethodProperty() {
        return samplingMethod;
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
}
