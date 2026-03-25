package com.ugcs.geohammer.chart.tool.projection.model;

import com.ugcs.geohammer.chart.tool.projection.math.Normal;
import javafx.geometry.Point2D;

public record GridSample(
        // sample position
        Point2D position,
        // below surface
        float depth,
        Normal normal,
        float value
) {
}
