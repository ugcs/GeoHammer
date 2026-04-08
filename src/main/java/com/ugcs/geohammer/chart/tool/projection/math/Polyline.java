package com.ugcs.geohammer.chart.tool.projection.math;

import com.ugcs.geohammer.math.KdTree;
import com.ugcs.geohammer.util.Check;
import javafx.geometry.Point2D;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Polyline {

    private final List<Point2D> points;

    private final KdTree<Integer> index;

    public Polyline(List<Point2D> points) {
        Check.notNull(points);
        Check.notEmpty(points);

        this.points = points;

        // points index
        int n = points.size();
        List<Integer> indices = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            indices.add(i);
        }
        index = KdTree.buildTree(points, indices);
    }

    public List<Point2D> getPoints() {
        return Collections.unmodifiableList(points);
    }

    public int numPoints() {
        return points.size();
    }

    public Point2D getPoint(int index) {
        return points.get(index);
    }

    private SegmentPoint projectToSegment(Point2D p, int segment) {
        Point2D a = points.get(segment);
        Point2D b = points.get(segment + 1);
        Point2D ab = b.subtract(a);
        Point2D ap = p.subtract(a);

        double ab2 = ab.dotProduct(ab); // length^2
        double t = ab2 > Vectors.EPS
                ? Math.clamp(ap.dotProduct(ab) / ab2, 0, 1)
                : 0;
        Point2D q = a.add(ab.multiply(t));
        return new SegmentPoint(segment, t, q);
    }

    public SegmentPoint project(Point2D p) {
        Check.notNull(p);

        int n = points.size();
        if (n == 1) {
            return new SegmentPoint(0, 0, points.getFirst());
        }

        KdTree.Neighbor<Integer> nearest = index.nearestNeighbor(p);
        int nearestIndex = nearest.value();

        SegmentPoint a = nearestIndex > 0
                ? projectToSegment(p, nearestIndex - 1)
                : null;
        SegmentPoint b = nearestIndex < n - 1
                ? projectToSegment(p, nearestIndex)
                : null;

        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        Point2D pa = a.point().subtract(p);
        Point2D pb = b.point().subtract(p);
        return pa.dotProduct(pa) < pb.dotProduct(pb) ? a : b;
    }

    public int findSegment(double x) {
        int n = points.size();
        if (n == 1 || x <= points.getFirst().getX()) {
            return 0;
        }
        if (x >= points.getLast().getX()) {
            return n - 2;
        }

        int l = 0;
        int r = n - 1;
        while (l <= r) {
            int m = (l + r) >>> 1;
            double xm = points.get(m).getX();
            if (x > xm) {
                l = m + 1;
            } else {
                r = m - 1;
            }
        }
        // l is first index where points[l].x >= x
        // segment is between l - 1 and l
        return l - 1;
    }

    private SegmentPoint intersectRayWithSegment(Point2D p, Point2D r, int segment) {
        Point2D q = points.get(segment);
        Point2D s = points.get(segment + 1).subtract(q);

        // p + tr = q + us
        // 1: cross both parts by x s and solve for t
        // t = (q - p) x s / (r x s)
        // 2: cross both parts by x r and solve for u
        // u = (p - q) x r / (s x r)
        // u = (q - p) x r / (r x s) as s x r = - r x s

        double rxs = Vectors.crossProduct(r, s);
        if (Math.abs(rxs) < Vectors.EPS) {
            return null;
        }

        Point2D qp = q.subtract(p);
        double qpxr = Vectors.crossProduct(qp, r);
        double qpxs = Vectors.crossProduct(qp, s);

        double t = qpxs / rxs;
        double u = qpxr / rxs;
        return t >= 0 && u >= -Vectors.EPS && u <= 1 + Vectors.EPS
                ? new SegmentPoint(segment, t, q.add(s.multiply(u)))
                : null;
    }

    public SegmentPoint intersectRay(Point2D p, Point2D r) {
        Check.notNull(p);
        Check.notNull(r);

        int n = points.size();
        if (n < 2) {
            return null; // nothing to intersect
        }

        // segment where ray originates
        int segment = findSegment(p.getX());
        int increment = r.getX() >= 0 ? 1 : -1;
        while (segment >= 0 && segment < n - 1) {
            SegmentPoint intersection = intersectRayWithSegment(p, r, segment);
            if (intersection != null) {
                return intersection;
            }
            segment += increment;
        }
        return null;
    }

    public Point2D getNormal(int segment) {
        if (points.size() == 1) {
            return new Point2D(0, 1);
        }
        segment = Math.clamp(segment, 0, points.size() - 2);
        Point2D a = points.get(segment);
        Point2D b = points.get(segment + 1);
        Point2D ab = b.subtract(a);

        Point2D normal = new Point2D(-ab.getY(), ab.getX()).normalize();
        // ensure normal points up
        if (normal.getY() < 0) {
            normal = normal.multiply(-1);
        }
        return normal;
    }

    public record SegmentPoint(
            int segment,
            double t,
            Point2D point
    ) {
    }
}
