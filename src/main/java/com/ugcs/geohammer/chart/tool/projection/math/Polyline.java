package com.ugcs.geohammer.chart.tool.projection.math;

import com.ugcs.geohammer.util.Check;
import javafx.geometry.Point2D;

import java.util.List;

public class Polyline {

    private final List<Point2D> points;

    public Polyline(List<Point2D> points) {
        Check.notNull(points);
        Check.condition(points.size() > 1);

        this.points = points;
    }

    public int numPoints() {
        return points.size();
    }

    public Point2D getPoint(int index) {
        return points.get(index);
    }

    public Projection project(Point2D p) {
        double minpq2 = Double.MAX_VALUE;
        Projection best = new Projection(0, 0, points.getFirst());
        for (int i = 0; i < points.size() - 1; i++) {
            Point2D a = points.get(i);
            Point2D b = points.get(i + 1);
            Point2D ab = b.subtract(a);
            Point2D ap = p.subtract(a);

            double ab2 = ab.dotProduct(ab); // length^2
            double t;
            if (ab2 < 1e-18) {
                t = 0;
            } else {
                t = ap.dotProduct(ab) / ab2;
                t = Math.clamp(t, 0, 1);
            }

            Point2D q = a.add(ab.multiply(t));
            Point2D pq = q.subtract(p);
            double pq2 = pq.dotProduct(pq);

            if (pq2 < minpq2) {
                minpq2 = pq2;
                best = new Projection(i, t, q);
            }
        }
        return best;
    }

    public record Projection(int segmentIndex, double t, Point2D point) {
    }
}
