package com.ugcs.geohammer.util;

import com.ugcs.geohammer.chart.tool.projection.math.Vectors;
import com.ugcs.geohammer.math.SphericalMercator;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.model.TraceKey;
import com.ugcs.geohammer.format.GeoData;

import javafx.geometry.Point2D;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class Traces {

    private static final double MIN_SEGMENT_LENGTH = 0.5;

    private Traces() {
    }

    public static IndexRange maxSampleRange(List<Trace> traces) {
        Integer from = null;
        Integer to = null;
        for (Trace trace : Nulls.toEmpty(traces)) {
            IndexRange range = trace.getSampleRange();
            if (range == null) {
                continue;
            }
            from = from != null
                    ? Math.min(from, range.from())
                    : range.from();
            to = to != null
                    ? Math.max(to, range.to())
                    : range.to();
        }
        if (from != null && to != null) {
            return new IndexRange(from, to);
        }
        return null;
    }

    public static List<Trace> copy(List<Trace> traces, IndexRange range) {
        traces = Nulls.toEmpty(traces);

        int fromIndex = range != null ? range.from() : 0;
        int toIndex = range != null ? range.to() : traces.size(); // exclusive

        List<Trace> newTraces = new ArrayList<>(toIndex - fromIndex);
        for (int i = fromIndex; i < toIndex; i++) {
            Trace trace = traces.get(i);
            Trace newTrace = trace.copy();
            // update trace index
            newTrace.setIndex(newTraces.size());
            newTraces.add(newTrace);
        }
        return newTraces;
    }

    public static List<Trace> copy(List<Trace> traces) {
        return copy(traces, null);
    }

    public static int findNearestTraceIndex(SgyFile file, LatLon latlon) {
        return findNearestTrace(file, latlon)
                .map(TraceKey::getIndex)
                .orElse(0);
    }

    public static Optional<TraceKey> findNearestTrace(SgyFile file, LatLon latLon) {
        return findNearestTrace(file, latLon, Double.MAX_VALUE);
    }

    public static Optional<TraceKey> findNearestTrace(SgyFile file, LatLon latlon,
            double distanceLimit) {
        return findNearestTraceInFiles(List.of(file), latlon, distanceLimit);
    }

    public static Optional<TraceKey> findNearestTraceInFiles(Iterable<SgyFile> files, LatLon latlon) {
        return findNearestTraceInFiles(files, latlon, Double.MAX_VALUE);
    }

    public static Optional<TraceKey> findNearestTraceInFiles(Iterable<SgyFile> files, LatLon latlon,
            double distanceLimit) {
        if (files == null) {
            return Optional.empty();
        }
        if (latlon == null) {
            return Optional.empty();
        }

        Point2D target = SphericalMercator.project(latlon);
		TraceKey nearest = null;
        double minDistance = Double.MAX_VALUE;
        for (SgyFile file : files) {
            List<GeoData> values = Nulls.toEmpty(file.getGeoData());

            int fromIndex = -1;
            Point2D from = null;
            for (int i = 0; i < values.size(); i++) {
                LatLon valueLatLon = values.get(i).getLatLon();
                if (valueLatLon == null) {
                    continue;
                }
                Point2D to = SphericalMercator.project(valueLatLon);

                int index;
                double distance;
                if (from != null && from.distance(to) >= MIN_SEGMENT_LENGTH) {
                    Point2D ab = to.subtract(from);
					Point2D ap = target.subtract(from);
                    double t = projection(ab, ap);
                    distance = target.distance(from.add(ab.multiply(t)));
                    // take a segment endpoint closest to the projection point
                    index = t < 0.5 ? fromIndex : i;
                } else {
                    distance = target.distance(to);
                    index = i;
                }

                if (distance < minDistance && distance <= distanceLimit) {
                    nearest = new TraceKey(file, index);
                    minDistance = distance;
                }
                from = to;
                fromIndex = i;
            }
        }
        return Optional.ofNullable(nearest);
    }

    private static double projection(Point2D ab, Point2D ap) {
        double ab2 = ab.dotProduct(ab);
        return ab2 > Vectors.EPS
                ? Math.clamp(ap.dotProduct(ab) / ab2, 0, 1)
                : 0;
    }
}
