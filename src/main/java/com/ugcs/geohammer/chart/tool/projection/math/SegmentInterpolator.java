package com.ugcs.geohammer.chart.tool.projection.math;

import javafx.geometry.Point2D;

public interface SegmentInterpolator {

    Point2D interpolate(Point2D a, Point2D b, double t);
}
