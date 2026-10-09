package com.ugcs.geohammer.util;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.math.interpolation.Interpolator;
import com.ugcs.geohammer.model.Column;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.model.Semantic;

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

    // fills empty values of the displayed data columns by linear interpolation
    // within each line
    public static void fillGeoDataValues(List<GeoData> values) {
        if (Nulls.isNullOrEmpty(values)) {
            return;
        }

        ColumnSchema schema = GeoData.getSchema(values);
        List<Integer> columnIndices = getColumnsToFill(schema);
        if (columnIndices.isEmpty()) {
            return;
        }

        int n = values.size();
        int l = 0;
        while (l < n) {
            Integer line = values.get(l).getLine();
            int r = l + 1;
            while (r < n && Objects.equals(values.get(r).getLine(), line)) {
                r++;
            }
            fillLineValues(values.subList(l, r), columnIndices);
            l = r;
        }
    }

    private static List<Integer> getColumnsToFill(ColumnSchema schema) {
        if (schema == null) {
            return List.of();
        }
        List<Integer> indices = new ArrayList<>(schema.numColumns());
        for (Column column : schema) {
            if (column.isDisplay() && !Objects.equals(column.getSemantic(), Semantic.LINE.getName())) {
                indices.add(schema.getColumnIndex(column.getHeader()));
            }
        }
        return indices;
    }

    private static void fillLineValues(List<GeoData> line, List<Integer> columnIndices) {
        Check.notNull(line);

        int n = line.size();
        double[] times = null;
        double[] y = null;
        for (int columnIndex : columnIndices) {
            if (!hasMissingValues(line, columnIndex)) {
                continue;
            }

            if (times == null) {
                times = getMonotonicTimes(line);
                y = new double[n];
            }

            for (int i = 0; i < n; i++) {
                Number number = line.get(i).getNumber(columnIndex);
                y[i] = number != null ? number.doubleValue() : Double.NaN;
            }

            Interpolator.linear().interpolate(times, y);

            for (int i = 0; i < n; i++) {
                GeoData value = line.get(i);
                if (value.getValue(columnIndex) == null && !Double.isNaN(y[i])) {
                    value.setValue(columnIndex, y[i]);
                }
            }
        }
    }

    private static boolean hasMissingValues(List<GeoData> values, int columnIndex) {
        for (GeoData value : Nulls.toEmpty(values)) {
            if (value.getValue(columnIndex) == null) {
                return true;
            }
        }
        return false;
    }

    // strictly monotonic row positions: missing times are interpolated,
    // rows before the first time are extrapolated back from it,
    // no times at all fall back to the row index
    private static double[] getMonotonicTimes(List<GeoData> values) {
        Check.notNull(values);

        int n = values.size();
        double[] times = new double[n];
        for (int i = 0; i < n; i++) {
            Long time = values.get(i).getTimestamp();
            times[i] = time != null ? (double)time : Double.NaN;
        }

        fillMonotonicPrefix(times);
        Interpolator.linear().makeMonotonic(times);
        spreadEqualValues(times);
        return times;
    }

    private static void fillMonotonicPrefix(double[] values) {
        Check.notNull(values);

        int n = values.length;
        int first = 0;
        while (first < n && !Double.isFinite(values[first])) {
            first++;
        }
        if (first == 0 || first == n) {
            return;
        }
        int second = first + 1;
        while (second < n && (!Double.isFinite(values[second]) || values[second] == values[first])) {
            second++;
        }
        if (second == n) {
            return;
        }
        double step = (values[second] - values[first]) / (second - first);
        for (int i = first - 1; i >= 0; i--) {
            values[i] = values[i + 1] - step;
        }
    }

    private static void spreadEqualValues(double[] values) {
        Check.notNull(values);

        int n = values.length;
        int l = 0;
        double step = 1.0;
        while (l < n) {
            int r = l + 1;
            while (r < n && values[l] == values[r]) {
                r++;
            }
            if (r < n) {
                step = (values[r] - values[l]) / (r - l);
            }
            for (int i = l + 1; i < r; i++) {
                values[i] = values[l] + step * (i - l);
            }
            l = r;
        }
    }
}
