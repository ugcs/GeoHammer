package com.ugcs.gprvisualizer.utils;

import com.github.thecoldwine.sigrun.common.ext.LatLon;
import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.github.thecoldwine.sigrun.common.ext.Trace;
import com.github.thecoldwine.sigrun.common.ext.TraceKey;
import com.ugcs.gprvisualizer.app.parcers.GeoData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class Traces {

    private Traces() {
    }

    public static List<Trace> copy(List<Trace> traces, Range range) {
        traces = Nulls.toEmpty(traces);

        int fromIndex = range != null
                ? range.getMin().intValue()
                : 0;
        int toIndex = range != null
                ? range.getMax().intValue() + 1
                : traces.size(); // exclusive

        List<Trace> newTraces = new ArrayList<>(toIndex - fromIndex);
        for (int i = fromIndex; i < toIndex; i++) {
            Trace trace = traces.get(i);
            Trace newTrace = new Trace(
                    trace.getBinHeader(),
                    trace.getHeader(),
                    Arrays.copyOf(trace.getNormValues(), trace.getNormValues().length),
                    trace.getLatLon());
            // update trace index
            newTrace.setIndex(newTraces.size());
            newTraces.add(newTrace);
        }
        return newTraces;
    }

    public static List<Trace> copy(List<Trace> traces) {
        return copy(traces, null);
    }

    public static void fillMissingLatLon(List<Trace> traces) {
        if (traces == null) {
            return;
        }

        Integer firstMissingIndex = null;
        LatLon latlon = null;
        for (int i = 0; i < traces.size(); i++) {
            Trace trace = traces.get(i) ;

            if (trace.getLatLon() == null) {
                if (latlon != null) {
                    trace.setLatLon(latlon);
                } else if (firstMissingIndex == null) {
                    firstMissingIndex = i;
                }
            }

            if (trace.getLatLon() != null) {
                latlon = trace.getLatLon();

                if (firstMissingIndex != null) {
                    for (int j = firstMissingIndex; j < i; j++) {
                        traces.get(j).setLatLon(latlon);
                    }
                    firstMissingIndex = null;
                }
            }
        }
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

        TraceKey nearest = null;
        double minDistance = Double.MAX_VALUE;
        for (SgyFile file : files) {
            List<GeoData> values = Nulls.toEmpty(file.getGeoData());
            for (int i = 0; i < values.size(); i++) {
                GeoData value = values.get(i);
                LatLon p = value.getLatLon();
                if (p == null) {
                    continue;
                }
                double d = latlon.getDistance(p);
                if (d < minDistance && d <= distanceLimit) {
                    nearest = new TraceKey(file, i);
                    minDistance = d;
                }
            }
        }
        return Optional.ofNullable(nearest);
    }
}
