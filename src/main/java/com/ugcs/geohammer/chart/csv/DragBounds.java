package com.ugcs.geohammer.chart.csv;

import javafx.geometry.Point2D;

public class DragBounds {

    private Point2D start;

    private Point2D stop;

    public DragBounds(Point2D start, Point2D stop) {
        this.start = start;
        this.stop = stop;
    }

    public Point2D getStart() {
        return start;
    }

    public void setStart(Point2D start) {
        this.start = start;
    }

    public Point2D getStop() {
        return stop;
    }

    public void setStop(Point2D stop) {
        this.stop = stop;
    }

    public double getMinX() {
        return Math.min(start.getX(), stop.getX());
    }

    public double getMinY() {
        return Math.min(start.getY(), stop.getY());
    }

    public double getMaxX() {
        return Math.max(start.getX(), stop.getX());
    }

    public double getMaxY() {
        return Math.max(start.getY(), stop.getY());
    }

    public double getWidth() {
        return Math.abs(stop.getX() - start.getX());
    }

    public double getHeight() {
        return Math.abs(stop.getY() - start.getY());
    }
}
