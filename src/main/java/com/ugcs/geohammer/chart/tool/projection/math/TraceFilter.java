package com.ugcs.geohammer.chart.tool.projection.math;

import javafx.geometry.Point2D;

import java.util.List;

public interface TraceFilter {

    List<Point2D> filter(List<Point2D> points);
}
