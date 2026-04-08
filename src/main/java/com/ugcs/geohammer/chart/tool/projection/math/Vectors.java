package com.ugcs.geohammer.chart.tool.projection.math;

import javafx.geometry.Point2D;

public final class Vectors {

    public static final double EPS = 1e-9;

    private Vectors() {
    }

    // ccw
    public static double angleFromDown(Point2D v) {
        return Math.atan2(v.getX(), -v.getY());
    }

    public static Point2D directionFromDown(double angle) {
        return new Point2D(Math.sin(angle), -Math.cos(angle));
    }

    public static double crossProduct(Point2D a, Point2D b) {
        return a.getX() * b.getY() - a.getY() * b.getX();
    }

    public static Point2D weightedBisector(Point2D a, Point2D b, double t) {
        t = Math.clamp(t, 0, 1);
        return a.multiply(1 - t)
                .add(b.multiply(t))
                .normalize();
    }
}
