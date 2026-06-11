package com.ugcs.geohammer.util;

import java.time.Instant;
import java.util.List;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.math.interpolation.Interpolator;
import com.ugcs.geohammer.model.LatLon;

public final class MissingValues {

	private MissingValues() {
	}

    public static boolean hasMissingGeoDataPositions(List<GeoData> values) {
        if (Nulls.isNullOrEmpty(values)) {
            return false;
        }
        for (GeoData value : values) {
            if (value.getLatitude() == null || value.getLongitude() == null) {
                return true;
            }
        }
        return false;
    }

    public static void fillGeoDataPositions(List<GeoData> values) {
        if (!hasMissingGeoDataPositions(values)) {
            return;
        }

        int n = values.size();
        double[] times = new double[n];
        double[] latitudes = new double[n];
        double[] longitudes = new double[n];

        for (int i = 0; i < n; i++) {
            GeoData value = values.get(i);

            Long time = value.getTimestamp();
            times[i] = time != null ? (double)time : Double.NaN;

            Double latitude = value.getLatitude();
            latitudes[i] = latitude != null ? latitude : Double.NaN;

            Double longitude = value.getLongitude();
            longitudes[i] = longitude != null ? longitude : Double.NaN;
        }

        Interpolator interpolator = Interpolator.spline();
        interpolator.interpolate(times, latitudes);
        interpolator.interpolate(times, longitudes);

        for (int i = 0; i < n; i++) {
            GeoData value = values.get(i);
            if (value.getLatitude() == null && !Double.isNaN(latitudes[i])) {
                value.setLatitude(latitudes[i]);
            }
            if (value.getLongitude() == null && !Double.isNaN(longitudes[i])) {
                value.setLongitude(longitudes[i]);
            }
        }
    }

    public static boolean hasMissingTracePositions(List<Trace> traces) {
        if (Nulls.isNullOrEmpty(traces)) {
            return false;
        }
        for (Trace trace : traces) {
            if (trace.getLatLon() == null) {
                return true;
            }
        }
        return false;
    }

    public static void fillTracePositions(List<Trace> traces) {
        if (!hasMissingTracePositions(traces)) {
            return;
        }

        int n = traces.size();
        double[] times = new double[n];
        double[] latitudes = new double[n];
        double[] longitudes = new double[n];

        for (int i = 0; i < n; i++) {
            Trace trace = traces.get(i);

            Instant time = trace.getDateTime();
            times[i] = time != null ? (double)time.toEpochMilli() : Double.NaN;

            LatLon position = trace.getLatLon();
            latitudes[i] = position != null ? position.getLatDgr() : Double.NaN;
            longitudes[i] = position != null ? position.getLonDgr() : Double.NaN;
        }

        Interpolator interpolator = Interpolator.spline();
        interpolator.interpolate(times, latitudes);
        interpolator.interpolate(times, longitudes);

        for (int i = 0; i < n; i++) {
            Trace trace = traces.get(i);
            if (trace.getLatLon() == null
                    && !Double.isNaN(latitudes[i])
                    && !Double.isNaN(longitudes[i])) {
                trace.setLatLon(new LatLon(latitudes[i], longitudes[i]));
            }
        }
    }
}
