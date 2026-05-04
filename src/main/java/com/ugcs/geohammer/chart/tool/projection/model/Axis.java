package com.ugcs.geohammer.chart.tool.projection.model;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Point2D;
import org.springframework.stereotype.Component;

@Component
public class Axis {

    private final ObjectProperty<Point2D> origin = new SimpleObjectProperty<>(Point2D.ZERO);

    public Point2D getOrigin() {
        return origin.get();
    }

    public ObjectProperty<Point2D> originProperty() {
        return origin;
    }
}
