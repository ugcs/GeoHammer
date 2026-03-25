package com.ugcs.geohammer.chart.tool.projection.model;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.springframework.stereotype.Component;

@Component
public class ProjectionResult {

    private final ObjectProperty<TraceProfile> profile = new SimpleObjectProperty<>();

    private final ObjectProperty<Grid> grid = new SimpleObjectProperty<>();

    public TraceProfile getProfile() {
        return profile.get();
    }

    public ObjectProperty<TraceProfile> profileProperty() {
        return profile;
    }

    public Grid getGrid() {
        return grid.get();
    }

    public ObjectProperty<Grid> gridProperty() {
        return grid;
    }
}
