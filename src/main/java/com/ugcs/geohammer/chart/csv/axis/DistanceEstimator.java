package com.ugcs.geohammer.chart.csv.axis;

import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.math.SphericalMercator;
import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.math.DouglasPeucker;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.util.Nulls;
import javafx.geometry.Point2D;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public class DistanceEstimator {

    private static final double APPROXIMATION_THRESHOLD = 0.05;

    private final NavigableMap<Integer, Anchor> anchors;

    public DistanceEstimator(NavigableMap<Integer, Anchor> anchors) {
        Check.notNull(anchors);

        this.anchors = anchors;
    }

    public double getDistanceAtTrace(int traceIndex, LatLon traceLocation) {
        Check.notNull(traceLocation);

        Map.Entry<Integer, Anchor> floor = anchors.floorEntry(traceIndex);
        if (floor == null) {
            return 0.0;
        }

        Anchor anchor = floor.getValue();
        return floor.getKey().equals(traceIndex)
                ? anchor.distance()
                : anchor.distance() + anchor.location().getDistance(traceLocation);
    }

    private static NavigableMap<Integer, Anchor> buildAnchors(SgyFile file) {
        Check.notNull(file);

        List<GeoData> values = Nulls.toEmpty(file.getGeoData());

        // projection scale factor
        double k = !values.isEmpty()
                ? SphericalMercator.scaleFactorAt(values.getFirst().getLatitude())
                : 1.0;

        NavigableMap<Integer, Anchor> anchors = new TreeMap<>();
        for (IndexRange range : file.getLineRanges().values()) {
            int from = range.from();
            int to = range.to();
            int numPoints = range.size();

            // project
            List<Point2D> points = new ArrayList<>(numPoints);
            for (int i = from; i < to; i++) {
                Point2D point = SphericalMercator.project(values.get(i).getLatLon());
                points.add(point);
            }

            // approximate polyline
            List<Integer> selected = DouglasPeucker.approximatePolyline(points,
                    k * APPROXIMATION_THRESHOLD, 1);

            // cumulative distance from start of line
            double d = 0.0;
            // index of line trace up to which
            // cumulative distance was computed
            int di = 0;
            for (int i : selected) {
                if (i == numPoints - 1) {
                    continue; // skip last point
                }
                // calculate distance up to i
                while (di < i) {
                    LatLon p1 = values.get(from + di).getLatLon();
                    LatLon p2 = values.get(from + di + 1).getLatLon();
                    d += p2.getDistance(p1);
                    di++;
                }
                Anchor anchor = new Anchor(values.get(from + i).getLatLon(), d);
                anchors.put(from + i, anchor);
            }
        }
        return anchors;
    }

    public static DistanceEstimator build(SgyFile file) {
        return new DistanceEstimator(buildAnchors(file));
    }

    public record Anchor(LatLon location, double distance) {
    }
}
