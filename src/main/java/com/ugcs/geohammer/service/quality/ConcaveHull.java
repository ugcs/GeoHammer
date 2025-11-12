package com.ugcs.geohammer.service.quality;

import com.ugcs.geohammer.util.Check;
import org.locationtech.jts.algorithm.ConvexHull;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.index.strtree.STRtree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class ConcaveHull {

    private final GeometryFactory gf = new GeometryFactory();

    private List<Coordinate> removeDuplicates(List<Coordinate> points) {
        if (points == null) {
            return List.of();
        }

        record Entry(double x, double y) {}
        Set<Entry> seen = new HashSet<>();

        List<Coordinate> unique = new ArrayList<>();
        for (Coordinate point : points) {
            if (seen.add(new Entry(point.getX(), point.getY()))) {
                unique.add(point);
            }
        }
        return unique;
    }

    public Polygon concaveHull(List<Coordinate> points, double convexity, double edgeThreshold) {
        Check.condition(convexity > 0);

        points = removeDuplicates(points);
        if (points.size() < 3) {
            return null;
        }

        Polygon convexHull = convexHull(points);
        if (convexHull == null) {
            return null;
        }

        List<Coordinate> convexShell = Arrays.asList(convexHull.getExteriorRing().getCoordinates());
        Set<Coordinate> convexPoints = new HashSet<>(convexShell);
        STRtree remaining = new STRtree(16);
        for (Coordinate point : points) {
            if (!convexPoints.contains(point)) {
                remaining.insert(new Envelope(point), point);
            }
        }

        List<Coordinate> concaveShell = new ArrayList<>();
        // first point added twice on both start and end
        LinkedList<Coordinate> q = new LinkedList<>(convexShell);

        LineSegment prev = new LineSegment(
                convexShell.get(convexShell.size() - 2),
                convexShell.get(convexShell.size() - 1));
        while (q.size() > 1) {
            // start of the current segment
            Coordinate point = q.pollFirst();
            // current dig segment
            LineSegment curr = new LineSegment(point, q.get(0));
            LineSegment next = q.size() > 1
                    ? new LineSegment(q.get(0), q.get(1))
                    : new LineSegment(concaveShell.get(0), concaveShell.get(1));

            double l = curr.getLength();
            if (l <= edgeThreshold) {
                concaveShell.add(point);
                // update previous segment
                prev = new LineSegment(prev.getCoordinate(1), point);
                continue;
            }

            double digDepth = l / convexity;
            Polygon digBounds = Spatial.orthoBuffer(curr, digDepth);
            List<Coordinate> candidates = findDigCandidates(remaining, digBounds);
            Coordinate nearest = getNearestPoint(candidates, curr, prev, next);

            if (nearest != null) {
                q.addFirst(nearest);
                q.addFirst(point);
                // remove point from search index
                remaining.remove(new Envelope(nearest), nearest);
            } else {
                concaveShell.add(point);
                // update previous segment
                prev = new LineSegment(prev.getCoordinate(1), point);
            }
        }
        // close concave shell
        concaveShell.add(concaveShell.getFirst());
        return gf.createPolygon(concaveShell.toArray(new Coordinate[0]));
    }

    private Polygon convexHull(List<Coordinate> points) {
        ConvexHull convexHull = new ConvexHull(points.toArray(new Coordinate[0]), gf);
        if (convexHull.getConvexHull() instanceof Polygon polygon) {
            return polygon;
        }
        return null;
    }

    private List<Coordinate> findDigCandidates(STRtree remaining, Polygon digBounds) {
        List<?> found = remaining.query(digBounds.getEnvelopeInternal());
        List<Coordinate> matching = new ArrayList<>(found.size());
        for (Object entry : found) {
            if (entry instanceof Coordinate point) {
                if (digBounds.covers(gf.createPoint(point))) {
                    matching.add(point);
                }
            }
        }
        return matching;
    }

    private Coordinate getNearestPoint(List<Coordinate> points,
            LineSegment curr, LineSegment prev, LineSegment next) {
        if (points == null) {
            return null;
        }
        Coordinate nearest = null;
        double min = Double.MAX_VALUE;
        for (Coordinate point : points) {
            double d = curr.distancePerpendicular(point);
            if (prev != null && prev.distance(point) < d) {
                continue;
            }
            if (next != null && next.distance(point) < d) {
                continue;
            }
            if (nearest == null || d < min) {
                nearest = point;
                min = d;
            }
        }
        return nearest;
    }
}
