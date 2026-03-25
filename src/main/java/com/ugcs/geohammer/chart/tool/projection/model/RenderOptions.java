package com.ugcs.geohammer.chart.tool.projection.model;

import com.ugcs.geohammer.service.palette.SpectrumType;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.springframework.stereotype.Component;

@Component
public class RenderOptions {

    private final BooleanProperty showOrigins = new SimpleBooleanProperty(true);

    private final BooleanProperty showTerrain = new SimpleBooleanProperty(true);

    private final BooleanProperty showNormals = new SimpleBooleanProperty(false);

    private final BooleanProperty showGrid = new SimpleBooleanProperty(true);

    private final BooleanProperty removeBackground = new SimpleBooleanProperty(true);

    private final DoubleProperty maxGain = new SimpleDoubleProperty(16);

    private final DoubleProperty contrast = new SimpleDoubleProperty(50);

    private final ObjectProperty<SpectrumType> spectrumType = new SimpleObjectProperty<>(SpectrumType.GRAYSCALE);

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

    public boolean isShowGrid() {
        return showGrid.get();
    }

    public BooleanProperty showGridProperty() {
        return showGrid;
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
}
