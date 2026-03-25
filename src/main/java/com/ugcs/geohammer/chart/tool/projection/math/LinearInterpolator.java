package com.ugcs.geohammer.chart.tool.projection.math;

import javafx.geometry.Point2D;

public class LinearInterpolator implements SegmentInterpolator {

    @Override
    public Point2D interpolate(Point2D a, Point2D b, double t) {
        return new Point2D(
                a.getX() + (b.getX() - a.getX()) * t,
                a.getY() + (b.getY() - a.getY()) * t
        );
    }
}
