package com.ugcs.geohammer.chart.tool.projection.math;

import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Nulls;
import javafx.geometry.Point2D;

import java.util.ArrayList;
import java.util.List;

public class NormalDiffusion {

    private static final double DEFAULT_LAMBDA = 0.4;

    private static final int DEFAULT_NUM_ITERATIONS = 30;

    private final double lambda;

    private final int numIterations;

    public NormalDiffusion() {
        this(DEFAULT_LAMBDA, DEFAULT_NUM_ITERATIONS);
    }

    public NormalDiffusion(double lambda, int numIterations) {
        this.lambda = lambda;
        this.numIterations = numIterations;
    }

    public List<Normal> getNormals(List<Point2D> points, Polyline polyline) {
        if (Nulls.isNullOrEmpty(points)) {
            return List.of();
        }

        Check.notNull(polyline);
        List<Point2D> normals = initNormals(polyline);
        diffuseNormals(normals);

        List<Normal> result = new ArrayList<>();
        for (Point2D point : points) {
            Polyline.Projection projected = polyline.project(point);
            Point2D v = projected.point().subtract(point);

            Point2D n0 = normals.get(projected.segmentIndex());
            Point2D n1 = normals.get(projected.segmentIndex() + 1);
            Point2D normal = n0.multiply(1 - projected.t())
                    .add(n1.multiply(projected.t()))
                    .normalize();

            double alpha = 0.7;
            Point2D unit = normal
                    .multiply(alpha)
                    .add(v.normalize().multiply(1 - alpha))
                    .normalize();
            result.add(new Normal(unit, v.magnitude()));
        }
        return result;
    }

    private List<Point2D> initNormals(Polyline polyline) {
        int n = polyline.numPoints();
        List<Point2D> normals = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            Point2D previous = polyline.getPoint(Math.max(i - 1, 0));
            Point2D next = polyline.getPoint(Math.min(i + 1, n - 1));

            Point2D tangent = next.subtract(previous).normalize();
            Point2D normal = new Point2D(tangent.getY(), -tangent.getX());
            normals.add(normal);
        }
        return normals;
    }

    private void diffuseNormals(List<Point2D> normals) {
        int n = normals.size();
        for (int iteration = 0; iteration < numIterations; iteration++) {
            List<Point2D> copy = new ArrayList<>(normals);
            for (int i = 1; i < n - 1; i++) {
                Point2D normal = copy.get(i);
                Point2D previous = copy.get(i - 1);
                Point2D next = copy.get(i + 1);

                Point2D lap = previous.add(next).subtract(normal.multiply(2));
                Point2D updated = normal.add(lap.multiply(lambda)).normalize();
                normals.set(i, updated);
            }
        }
    }
}
