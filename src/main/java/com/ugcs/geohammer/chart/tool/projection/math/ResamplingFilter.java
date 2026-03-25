package com.ugcs.geohammer.chart.tool.projection.math;

import javafx.geometry.Point2D;

import java.util.ArrayList;
import java.util.List;

public class ResamplingFilter implements TraceFilter {

    private final SegmentInterpolator interpolator;

    private final double minStep;

    public ResamplingFilter(SegmentInterpolator interpolator, double minStep) {
        this.interpolator = interpolator;
        this.minStep = minStep;
    }

    @Override
    public List<Point2D> filter(List<Point2D> points) {
        if (points.size() < 2) {
            return List.copyOf(points);
        }

        double minX = points.getFirst().getX();
        double maxX = points.getLast().getX();
        double step = (maxX - minX) / (points.size() - 1);
        step = Math.max(step, minStep);

        List<Point2D> resampled = new ArrayList<>();

        int i = 0; // read index
        for (double x = minX; x <= maxX; x += step) {
            while (i < points.size() - 2 && points.get(i + 1).getX() < x) {
                i++;
            }
            Point2D a = points.get(i);
            Point2D b = points.get(i + 1);

            double dx = b.getX() - a.getX();
            double t = Math.abs(dx) > 1e-18 ? (x - a.getX()) / dx : 0;

            Point2D p = interpolator.interpolate(a, b, t);
            resampled.add(p);
        }
        if (resampled.getLast().getX() < maxX) {
            resampled.add(points.getLast());
        }
        return resampled;
    }
}
