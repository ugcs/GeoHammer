package com.ugcs.geohammer.math;

import Jama.EigenvalueDecomposition;
import Jama.Matrix;
import com.ugcs.geohammer.util.Check;
import javafx.geometry.Point2D;

import java.util.List;

public final class PrincipalComponents {

    private PrincipalComponents() {
    }

    public static Components2 findComponents(List<Point2D> points) {
        if (points == null || points.isEmpty()) {
            return new Components2(
                    new Point2D(0, 0),
                    new Point2D(1, 0),
                    new Point2D(0, 1));
        }

        double[][] features = new double[points.size()][2];
        for (int i = 0; i < features.length; i++) {
            Point2D point = points.get(i);
            features[i][0] = point.getX();
            features[i][1] = point.getY();
        }

        Matrix x = new Matrix(features);
        Matrix cov = covariance(x);

        EigenvalueDecomposition eigenPair = cov.eig();
        double[] eigenValues = eigenPair.getRealEigenvalues();
        Matrix eigenVectors = eigenPair.getV();

        Point2D c0 = new Point2D(eigenVectors.get(0, 0), eigenVectors.get(1, 0))
                .multiply(eigenValues[0]);
        Point2D c1 = new Point2D(eigenVectors.get(0, 1), eigenVectors.get(1, 1))
                .multiply(eigenValues[1]);
        return new Components2(centroid(points), c0, c1);
    }

    private static Matrix covariance(Matrix x) {
        int m = x.getRowDimension();
        int n = x.getColumnDimension();
        Matrix mean = new Matrix(1, n);
        for (int j = 0; j < n; j++) {
            double sum = 0;
            for (int i = 0; i < m; i++) {
                sum += x.get(i, j);
            }
            mean.set(0, j, sum / m);
        }
        Matrix xCentered = x.minus(new Matrix(m, 1, 1.0).times(mean));
        return xCentered.transpose().times(xCentered).times(1.0 / (m - 1));
    }

    private static Point2D centroid(List<Point2D> points) {
        double xSum = 0;
        double ySum = 0;
        for (Point2D point : points) {
            xSum += point.getX();
            ySum += point.getY();
        }
        int n = points.size();
        return new Point2D(xSum / n, ySum / n);
    }

    // shape components in a 2d space
    public static class Components2 {

        private final Point2D centroid;
        private final Point2D primary;
        private final Point2D secondary;
        // length ratio
        private final double ratio;

        public Components2(Point2D centroid, Point2D primary, Point2D secondary) {
            Check.notNull(centroid);
            Check.notNull(primary);
            Check.notNull(secondary);

            this.centroid = centroid;
            double l1 = primary.magnitude();
            double l2 = secondary.magnitude();
            this.ratio = Math.min(l1, l2) > 1e-9
                    ? Math.sqrt(Math.max(l1, l2) / Math.min(l1, l2))
                    : 0.0;
            if (l1 >= l2) {
                this.primary = primary;
                this.secondary = secondary;
            } else {
                this.primary = secondary;
                this.secondary = primary;
            }
        }

        public Point2D centroid() {
            return centroid;
        }

        public Point2D primary() {
            return primary;
        }

        public Point2D secondary() {
            return secondary;
        }

        public double ratio() {
            return ratio;
        }
    }
}