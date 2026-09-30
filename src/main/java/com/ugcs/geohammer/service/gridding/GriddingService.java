package com.ugcs.geohammer.service.gridding;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.math.QuickSelect;
import com.ugcs.geohammer.math.SphericalMercator;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.model.DataPoint;
import com.ugcs.geohammer.model.Semantic;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Nulls;
import edu.mines.jtk.interp.SplinesGridder2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GriddingService {

    private static final Logger log = LoggerFactory.getLogger(GriddingService.class);

    // test showed that 200 is the point where extra iterations stop paying off
    private static final int SPLINES_MAX_ITERATIONS = 200;

    // 0 overshoots near strong anomalies, and 0.999999 becomes unstable with more iterations
    private static final double SPLINES_TENSION = 0.9999;

    public GriddingService() {
    }

    public GriddingResult runGridding(Collection<SgyFile> files, String seriesName, GriddingParams params) {
        long filteringStart = System.nanoTime();

        List<DataPoint> dataPoints = getDataPoints(files, seriesName);
        if (dataPoints.isEmpty()) {
            return null;
        }

        Envelope envelope = Envelope.of(dataPoints);
        // add padding of blanking distance to envelope
        double padding = ((int) Math.ceil(params.blankingDistance() / params.cellSize()) + 1) * params.cellSize();
        envelope = envelope.expand(padding);

        double width = envelope.width();
        double height = envelope.height();

        int gridWidth = (int) (width / params.cellSize());
        int gridHeight = (int) (height / params.cellSize());
        if (gridWidth == 0 || gridHeight == 0) {
            return null;
        }

        Map<CellIndex, List<Double>> cellValues = new HashMap<>();
        for (DataPoint point : dataPoints) {
            CellIndex cellIndex = envelope.cellIndex(point, gridWidth, gridHeight);
            cellValues.computeIfAbsent(cellIndex, (k -> new ArrayList<>()))
                    .add(point.value());
        }

        float[][] grid = new float[gridWidth][gridHeight];
        boolean[][] mask = new boolean[gridWidth][gridHeight];
        for (int i = 0; i < gridWidth; i++) {
            Arrays.fill(mask[i], true);
        }

        float median = (float) QuickSelect.getMedian(dataPoints, DataPoint::value);

        for (Map.Entry<CellIndex, List<Double>> entry : cellValues.entrySet()) {
            CellIndex cellIndex = entry.getKey();
            int x = cellIndex.x();
            int y = cellIndex.y();
            // subtract global median from the value
            grid[x][y] = (float) calculateMedian(entry.getValue()) - median;
            mask[x][y] = false;
        }

        log.info("Filtering complete in {} ms", (int) ((System.nanoTime() - filteringStart) * 1e-6));

        if (Thread.currentThread().isInterrupted()) {
            log.info("Gridding interrupted");
            return null;
        }

        interpolateSplines(grid, mask);

        if (Thread.currentThread().isInterrupted()) {
            log.info("Gridding interrupted");
            return null;
        }

        // visibility mask
        int blankingRadius = (int) (params.blankingDistance() / params.cellSize());
        boolean[][] visiblePoints = new boolean[gridWidth][gridHeight];
        for (Map.Entry<CellIndex, List<Double>> entry : cellValues.entrySet()) {
            CellIndex cellIndex = entry.getKey();
            int x = cellIndex.x();
            int y = cellIndex.y();

            for (int dx = -blankingRadius; dx <= blankingRadius; dx++) {
                for (int dy = -blankingRadius; dy <= blankingRadius; dy++) {
                    int nx = x + dx;
                    int ny = y + dy;
                    if (nx >= 0 && nx < gridWidth && ny >= 0 && ny < gridHeight) {
                        visiblePoints[nx][ny] = true;
                    }
                }
            }
        }

        for (int i = 0; i < gridWidth; i++) {
            for (int j = 0; j < gridHeight; j++) {
                if (!visiblePoints[i][j]) {
                    grid[i][j] = Float.NaN;
                } else {
                    grid[i][j] += median;
                }
            }
        }

        return new GriddingResult(
                seriesName,
                grid,
                envelope.min(),
                envelope.max(),
                params
        );
    }

    private void interpolateSplines(float[][] grid, boolean[][] mask) {
        log.info("Splines interpolation");
        long interpolationStart = System.nanoTime();

        SplinesGridder2 gridder = new SplinesGridder2();
        gridder.setMaxIterations(SPLINES_MAX_ITERATIONS);
        gridder.setTension(SPLINES_TENSION);
        gridder.gridMissing(mask, grid);

        log.info("Iterations: {}/{}, tension: {}, time: {} ms",
                gridder.getIterationCount(),
                SPLINES_MAX_ITERATIONS,
                SPLINES_TENSION,
                (int) ((System.nanoTime() - interpolationStart) * 1e-6));
        log.info("Interpolation complete");
    }

    private List<DataPoint> getDataPoints(Collection<SgyFile> files, String seriesName) {
        List<DataPoint> points = new ArrayList<>();
        for (SgyFile file : files) {
            points.addAll(getDataPoints(file, seriesName));
        }
        return points;
    }

    private List<DataPoint> getDataPoints(SgyFile file, String seriesName) {
        if (file == null) {
            return List.of();
        }

        List<GeoData> values = Nulls.toEmpty(file.getGeoData());
        ColumnSchema schema = GeoData.getSchema(values);
        if (schema == null) {
            return List.of();
        }

        int latitudeIndex = schema.getColumnIndex(schema.getHeaderBySemantic(Semantic.LATITUDE.getName()));
        int longitudeIndex = schema.getColumnIndex(schema.getHeaderBySemantic(Semantic.LONGITUDE.getName()));
        int valueIndex = schema.getColumnIndex(seriesName);
        if (latitudeIndex == -1 || longitudeIndex == -1 || valueIndex == -1) {
            return List.of();
        }

        List<DataPoint> points = new ArrayList<>(values.size());
        for (GeoData value : values) {
            Number latitude = value.getNumber(latitudeIndex);
            Number longitude = value.getNumber(longitudeIndex);
            Number pointValue = value.getNumber(valueIndex);
            if (latitude == null || longitude == null || pointValue == null) {
                continue;
            }
            points.add(new DataPoint(
                    latitude.doubleValue(),
                    longitude.doubleValue(),
                    pointValue.doubleValue()));
        }
        return points;
    }

    private static double calculateMedian(List<Double> values) {
        return QuickSelect.getMedian(values, Double::doubleValue);
    }

    private record Envelope(LatLon min, LatLon max) {

        public double width() {
            return Math.max(
                    new LatLon(min.getLatDgr(), min.getLonDgr()).getDistance(new LatLon(min.getLatDgr(), max.getLonDgr())),
                    new LatLon(max.getLatDgr(), min.getLonDgr()).getDistance(new LatLon(max.getLatDgr(), max.getLonDgr())));
        }

        public double height() {
            return Math.max(
                    new LatLon(min.getLatDgr(), min.getLonDgr()).getDistance(new LatLon(max.getLatDgr(), min.getLonDgr())),
                    new LatLon(min.getLatDgr(), max.getLonDgr()).getDistance(new LatLon(max.getLatDgr(), max.getLonDgr())));
        }

        public CellIndex cellIndex(DataPoint point, int gridWidth, int gridHeight) {
            int x = (int) (gridWidth * (point.longitude() - min.getLonDgr()) / (max.getLonDgr() - min.getLonDgr()));
            int y = (int) (gridHeight * (point.latitude() - min.getLatDgr()) / (max.getLatDgr() - min.getLatDgr()));
            return new CellIndex(
                    Math.clamp(x, 0, gridWidth - 1),
                    Math.clamp(y, 0, gridHeight - 1));
        }

        public Envelope expand(double distance) {
            Check.condition(distance >= 0);

            double latitudeOffset = Math.toDegrees(distance / SphericalMercator.R);
            double minLatitude = Math.max(min.getLatDgr() - latitudeOffset, -90);
            double maxLatitude = Math.min(max.getLatDgr() + latitudeOffset, 90);

            // a degree of longitude is shortest on the edge farthest from the equator,
            // offsetting by it keeps both edges at least the distance away
            double cos = Math.cos(Math.toRadians(Math.max(Math.abs(minLatitude), Math.abs(maxLatitude))));
            double longitudeOffset = cos > 0
                    ? Math.toDegrees(distance / (SphericalMercator.R * cos))
                    : 180;
            double minLongitude = Math.max(min.getLonDgr() - longitudeOffset, -180);
            double maxLongitude = Math.min(max.getLonDgr() + longitudeOffset, 180);

            return new Envelope(
                    new LatLon(minLatitude, minLongitude),
                    new LatLon(maxLatitude, maxLongitude));
        }
        
        public static Envelope of(List<DataPoint> points) {
            Check.notEmpty(points);

            double minLatitude = Double.POSITIVE_INFINITY;
            double minLongitude = Double.POSITIVE_INFINITY;
            double maxLatitude = Double.NEGATIVE_INFINITY;
            double maxLongitude = Double.NEGATIVE_INFINITY;
            for (DataPoint point : points) {
                minLatitude = Math.min(minLatitude, point.latitude());
                minLongitude = Math.min(minLongitude, point.longitude());
                maxLatitude = Math.max(maxLatitude, point.latitude());
                maxLongitude = Math.max(maxLongitude, point.longitude());
            }
            return new Envelope(
                    new LatLon(minLatitude, minLongitude),
                    new LatLon(maxLatitude, maxLongitude));
        }
    }

    private record CellIndex(int x, int y) {}
}
