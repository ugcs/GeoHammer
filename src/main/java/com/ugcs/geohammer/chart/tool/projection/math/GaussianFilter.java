package com.ugcs.geohammer.chart.tool.projection.math;

import javafx.geometry.Point2D;

import java.util.ArrayList;
import java.util.List;

public class GaussianFilter implements TraceFilter {

    private final double radius;

    private final double sigma;

    public GaussianFilter(double radius) {
        this.radius = radius;
        this.sigma = radius / 2.0;
    }

    @Override
    public List<Point2D> filter(List<Point2D> points) {
        int n = points.size();
        List<Point2D> filtered = new ArrayList<>(n);

        double k = -1.0 / (2 * sigma * sigma);
        for (int i = 0; i < n; i++) {
            Point2D pi = points.get(i);
            double sum = 0;
            double wsum = 0;

            // left
            for (int j = i; j >= 0; j--) {
                Point2D pj = points.get(j);
                double dx = pi.getX() - pj.getX();
                if (dx > radius) {
                    break;
                }
                double w = Math.exp(k * dx * dx);
                sum += pj.getY() * w;
                wsum += w;
            }

            // right
            for (int j = i + 1; j < n; j++) {
                Point2D pj = points.get(j);
                double dx = pj.getX() - pi.getX();
                if (dx > radius) {
                    break;
                }
                double w = Math.exp(k * dx * dx);
                sum += pj.getY() * w;
                wsum += w;
            }

            filtered.add(new Point2D(
                    pi.getX(),
                    wsum != 0 ? sum / wsum : sum
            ));
        }
        return filtered;
    }
}
