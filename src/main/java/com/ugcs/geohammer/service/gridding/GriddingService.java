package com.ugcs.geohammer.service.gridding;

import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.math.QuickSelect;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.model.DataPoint;
import edu.mines.jtk.interp.SplinesGridder2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GriddingService {

    private static final Logger log = LoggerFactory.getLogger(GriddingService.class);

    public GriddingService() {
    }

    public GriddingResult runGridding(Collection<SgyFile> files, String seriesName, GriddingParams params) {
        long startFiltering = System.currentTimeMillis();

        List<DataPoint> dataPoints = new ArrayList<>();
        for (SgyFile file : files) {
            dataPoints.addAll(getDataPoints(file, seriesName));
        }
        if (dataPoints.isEmpty()) {
            return null;
        }

        double minLon = dataPoints.stream().mapToDouble(DataPoint::longitude).min().orElseThrow();
        double maxLon = dataPoints.stream().mapToDouble(DataPoint::longitude).max().orElseThrow();
        double minLat = dataPoints.stream().mapToDouble(DataPoint::latitude).min().orElseThrow();
        double maxLat = dataPoints.stream().mapToDouble(DataPoint::latitude).max().orElseThrow();

        LatLon minLatLon = new LatLon(minLat, minLon);
        LatLon maxLatLon = new LatLon(maxLat, maxLon);

        List<Double> valuesList = new ArrayList<>(dataPoints.stream().map(p -> p.value()).toList());
        float median = (float) calculateMedian(valuesList);

        double width = Math.max(
                new LatLon(minLat, minLon).getDistance(new LatLon(minLat, maxLon)),
                new LatLon(maxLat, minLon).getDistance(new LatLon(maxLat, maxLon)));
        double height = (int) Math.max(
                new LatLon(minLat, minLon).getDistance(new LatLon(maxLat, minLon)),
                new LatLon(minLat, maxLon).getDistance(new LatLon(maxLat, maxLon)));

        int gridWidth = (int) (width / params.cellSize());
        int gridHeight = (int) (height / params.cellSize());
        if (gridWidth == 0 || gridHeight == 0) {
            return null;
        }

        double lonStep = (maxLon - minLon) / gridWidth;
        double latStep = (maxLat - minLat) / gridHeight;

        float[][] grid = new float[gridWidth][gridHeight];

        boolean[][] m = new boolean[gridWidth][gridHeight];
        for (int i = 0; i < gridWidth; i++) {
            for (int j = 0; j < gridHeight; j++) {
                m[i][j] = true;
            }
        }

        Map<CellIndex, List<Double>> points = new HashMap<>();
        for (DataPoint point : dataPoints) {
            int xIndex = (int) ((point.longitude() - minLon) / lonStep);
            xIndex = Math.min(xIndex, gridWidth - 1);
            int yIndex = (int) ((point.latitude() - minLat) / latStep);
            yIndex = Math.min(yIndex, gridHeight - 1);

            points.computeIfAbsent(new CellIndex(xIndex, yIndex), (k -> new ArrayList<>()))
                    .add(point.value());
        }

        int blankingRadius = (int) (params.blankingDistance() / params.cellSize());
        boolean[][] visiblePoints = new boolean[gridWidth][gridHeight];

        for (Map.Entry<CellIndex, List<Double>> entry : points.entrySet()) {
            CellIndex cellIndex = entry.getKey();
            int xIndex = cellIndex.x();
            int yIndex = cellIndex.y();
            grid[xIndex][yIndex] = (float) calculateMedian(entry.getValue());
            m[xIndex][yIndex] = false;

            for (int dx = -blankingRadius; dx <= blankingRadius; dx++) {
                for (int dy = -blankingRadius; dy <= blankingRadius; dy++) {
                    int nx = xIndex + dx;
                    int ny = yIndex + dy;
                    if (nx >= 0 && nx < gridWidth && ny >= 0 && ny < gridHeight) {
                        visiblePoints[nx][ny] = true;
                    }
                }
            }
        }

        for (int i = 0; i < grid.length; i++) {
            for (int j = 0; j < grid[0].length; j++) {
                if (!m[i][j]) {
                    grid[i][j] -= median;
                }
            }
        }

        log.info("Filtering complete in {} s", (System.currentTimeMillis() - startFiltering) / 1000);

        if (Thread.currentThread().isInterrupted()) {
            log.info("Gridding interrupted");
            return null;
        }

        log.info("Splines interpolation");
        long start = System.currentTimeMillis();

        // splines interpolation
        SplinesGridder2 gridder = new SplinesGridder2();
        int maxIterations = 200;
        double tension = 0.9999;
        gridder.setMaxIterations(maxIterations);
        gridder.setTension(tension);
        gridder.gridMissing(m, grid);

        log.info("Iterations: {}, time: {} s, tension: {}, maxIterations: {}",
                gridder.getIterationCount(),
                (System.currentTimeMillis() - start) / 1000,
                tension,
                maxIterations);
        log.info("Interpolation complete");

        if (Thread.currentThread().isInterrupted()) {
            log.info("Gridding interrupted");
            return null;
        }

        for (int i = 0; i < grid.length; i++) {
            for (int j = 0; j < grid[0].length; j++) {
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
                minLatLon,
                maxLatLon,
                params
        );
    }

    private List<DataPoint> getDataPoints(SgyFile file, String seriesName) {
        return file.getGeoData().stream()
                .filter(gd -> gd.getNumber(seriesName) != null)
                .map(gd -> new DataPoint(gd.getLatitude(), gd.getLongitude(), gd.getNumber(seriesName).doubleValue()))
                .toList();
    }

    private static double calculateMedian(List<Double> values) {
        return QuickSelect.getMedian(values, Double::doubleValue);
    }

    private record CellIndex(int x, int y) {}
}
