package com.ugcs.geohammer.math;

import javafx.geometry.Point2D;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Queue;

public final class DouglasPeucker {

    private DouglasPeucker() {
    }

    // returns sorted list of selected indices
    // returned polyline is not closed (first point != last point),
    // even if input polyline was
    public static List<Integer> approximatePolyline(
            List<Point2D> points, double threshold, int minPoints) {
        if (points == null)
            return Collections.emptyList();

        int n = points.size();
        // omit last point if polyline is closed
        if (n > 1 && Objects.equals(points.get(0), points.get(n - 1))) {
            n = n - 1;
        }
        List<Integer> selected;
        if (n < 3) {
            selected = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                selected.add(i);
            }
        } else {
            // initial capacity is 1/4 of the source
            selected = new ArrayList<>(n >> 2);
            selected.add(0);
            selected.add(n - 1);
            // initial capacity for the intervals queue is 1/4
            // of the initial points count
            Queue<Interval> queue = new ArrayDeque<>(n >> 2);
            queue.add(new Interval(0, n - 1));

            while (!queue.isEmpty()) {
                Interval interval = queue.poll();
                int pivot = maxDeviation(points, interval,
                        selected.size() < minPoints ? 0.0 : threshold);
                if (pivot != -1) {
                    selected.add(pivot);
                    // split
                    queue.add(new Interval(interval.a, pivot));
                    queue.add(new Interval(pivot, interval.b));
                }
            }
        }
        selected.sort(Comparator.naturalOrder());
        if (selected.size() == 2) {
            Point2D a = points.get(selected.get(0));
            Point2D b = points.get(selected.get(1));
            if (b.subtract(a).magnitude() < threshold) {
                selected.remove(1);
            }
        }
        return selected;
    }

    static int maxDeviation(List<Point2D> points, Interval interval, double threshold) {
        Point2D a = points.get(interval.a);
        Point2D b = points.get(interval.b);
        Point2D u = b.subtract(a);
        double ul = u.magnitude();
        Point2D un = ul < 1e-9
                ? new Point2D(0, 0)
                : u.multiply(1.0 / ul);

        double maxd = 0.0;
        int maxi = -1;
        for (int i = interval.a + 1; i < interval.b; i++) {
            Point2D ap = points.get(i).subtract(a);
            double d = ul < 1e-9
                    ? ap.magnitude() // interval is close to point
                    : Math.abs(ap.getX() * un.getY() - ap.getY() * un.getX()); // = ap dot ortho(un)
            if (d > maxd) {
                maxd = d;
                maxi = i;
            }
        }
        return maxd >= threshold
                ? maxi
                : -1;
    }

    static class Interval {

        final int a;
        final int b;

        public Interval(int a, int b) {
            this.a = a;
            this.b = b;
        }
    }
}
