package com.ugcs.geohammer.chart.tool.projection.model;

import com.ugcs.geohammer.service.palette.SpectrumType;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Point2D;
import org.springframework.stereotype.Component;

@Component
public class RenderOptions {

    private final BooleanProperty showOrigins = new SimpleBooleanProperty(true);

    private final BooleanProperty showTerrain = new SimpleBooleanProperty(true);

    private final BooleanProperty showNormals = new SimpleBooleanProperty(true);

    private final BooleanProperty removeBackground = new SimpleBooleanProperty(true);

    private final DoubleProperty maxGain = new SimpleDoubleProperty(16);

    private final DoubleProperty contrast = new SimpleDoubleProperty(0.5);

    private final ObjectProperty<SpectrumType> spectrumType = new SimpleObjectProperty<>(SpectrumType.GRAYSCALE);

    private final ObjectProperty<Point2D> axisOrigin = new SimpleObjectProperty<>(Point2D.ZERO);

    public boolean isShowOrigins() {
        return showOrigins.get();
    }

    public BooleanProperty showOriginsProperty() {
        return showOrigins;
    }

    public boolean isShowTerrain() {
        return showTerrain.get();
    }

    public BooleanProperty showTerrainProperty() {
        return showTerrain;
    }

    public boolean isShowNormals() {
        return showNormals.get();
    }

    public BooleanProperty showNormalsProperty() {
        return showNormals;
    }

    public boolean isRemoveBackground() {
        return removeBackground.get();
    }

    public BooleanProperty removeBackgroundProperty() {
        return removeBackground;
    }

    public double getMaxGain() {
        return maxGain.get();
    }

    public DoubleProperty maxGainProperty() {
        return maxGain;
    }

    public double getContrast() {
        return contrast.get();
    }

    public DoubleProperty contrastProperty() {
        return contrast;
    }

    public SpectrumType getSpectrumType() {
        return spectrumType.get();
    }

    public ObjectProperty<SpectrumType> spectrumTypeProperty() {
        return spectrumType;
    }

    public Point2D getAxisOrigin() {
        return axisOrigin.get();
    }

    public ObjectProperty<Point2D> axisOriginProperty() {
        return axisOrigin;
    }
}
