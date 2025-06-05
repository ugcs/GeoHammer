package com.ugcs.gprvisualizer.utils;

import com.github.thecoldwine.sigrun.common.ext.LatLon;
import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.github.thecoldwine.sigrun.common.ext.TraceKey;
import com.ugcs.gprvisualizer.app.parcers.GeoData;

import java.util.List;
import java.util.Optional;

public class TraceUtils {

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
            for (GeoData value : Nulls.toEmpty(file.getGeoData())) {
                LatLon p = value.getLatLon();
                if (p == null) {
                    continue;
                }
                double d = latlon.getDistance(p);
                if (d < minDistance && d <= distanceLimit) {
                    nearest = new TraceKey(file, value.getTraceNumber());
                    minDistance = d;
                }
            }
        }
        return Optional.ofNullable(nearest);
    }
}
