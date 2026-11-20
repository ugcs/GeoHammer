package com.ugcs.geohammer.math;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import javafx.geometry.Point2D;
import org.junit.jupiter.api.Test;

class DouglasPeuckerTest {

    private static final double THRESHOLD = 1.5;

    @Test
    void approximatePolyline_deviationsBelowThreshold_keepsEndsOnly() {
        List<Point2D> points = List.of(
                new Point2D(0, 0),
                new Point2D(5, 0.5),
                new Point2D(10, -0.5),
                new Point2D(20, 0));

        List<Integer> selected = DouglasPeucker.approximatePolyline(points, THRESHOLD, 2);

        assertEquals(List.of(0, 3), selected);
    }

    @Test
    void approximatePolyline_polylineReturnsAlongItself_keepsTurnaroundBeyondLastPoint() {
        // out to x = -100 and halfway back on the same line:
        // the turnaround lies on the line through the ends, but far from the segment
        List<Point2D> points = List.of(
                new Point2D(100, 0),
                new Point2D(0, 0.5),
                new Point2D(-100, 0),
                new Point2D(-50, 0.5),
                new Point2D(0, 0));

        List<Integer> selected = DouglasPeucker.approximatePolyline(points, THRESHOLD, 2);

        assertEquals(List.of(0, 2, 4), selected);
    }

    @Test
    void approximatePolyline_polylineStartsAwayFromLastPoint_keepsTurnaroundBeforeFirstPoint() {
        List<Point2D> points = List.of(
                new Point2D(0, 0),
                new Point2D(-50, 0.5),
                new Point2D(-100, 0),
                new Point2D(0, 0.5),
                new Point2D(100, 0));

        List<Integer> selected = DouglasPeucker.approximatePolyline(points, THRESHOLD, 2);

        assertEquals(List.of(0, 2, 4), selected);
    }

    @Test
    void approximatePolyline_polylineEndsNextToStart_keepsTurnaround() {
        // ends are closer than the threshold and aligned with the polyline
        List<Point2D> points = List.of(
                new Point2D(0, 0),
                new Point2D(50, 0.5),
                new Point2D(100, 0),
                new Point2D(50, -0.5),
                new Point2D(1, 0));

        List<Integer> selected = DouglasPeucker.approximatePolyline(points, THRESHOLD, 2);

        assertEquals(List.of(0, 2, 4), selected);
    }

    @Test
    void approximatePolyline_shorterThanThresholdWithTwoMinPoints_keepsBothEnds() {
        List<Point2D> points = List.of(
                new Point2D(0, 0),
                new Point2D(0.5, 0.2),
                new Point2D(1, 0));

        List<Integer> selected = DouglasPeucker.approximatePolyline(points, THRESHOLD, 2);

        assertEquals(List.of(0, 2), selected);
    }

    @Test
    void approximatePolyline_shorterThanThresholdWithOneMinPoint_keepsFirstPointOnly() {
        List<Point2D> points = List.of(
                new Point2D(0, 0),
                new Point2D(0.5, 0.2),
                new Point2D(1, 0));

        List<Integer> selected = DouglasPeucker.approximatePolyline(points, THRESHOLD, 1);

        assertEquals(List.of(0), selected);
    }
}
