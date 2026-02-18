package com.ugcs.geohammer.service.gridding;

import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.model.DataPoint;
import edu.mines.jtk.interp.SplinesGridder2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GriddingService {

    private static final Logger log = LoggerFactory.getLogger(GriddingService.class);

    public GriddingService() {
    }

    public GriddingResult runGridding(Collection<SgyFile> files, String seriesName, GriddingParams params) {
        var startFiltering = System.currentTimeMillis();

        List<DataPoint> dataPoints = new ArrayList<>();
        for (SgyFile file : files) {
            dataPoints.addAll(getDataPoints(file, seriesName));
        }

        dataPoints = getMedianValues(dataPoints);
        if (dataPoints.isEmpty()) {
            return null;
        }

        double minLon = dataPoints.stream().mapToDouble(DataPoint::longitude).min().orElseThrow();
        double maxLon = dataPoints.stream().mapToDouble(DataPoint::longitude).max().orElseThrow();
        double minLat = dataPoints.stream().mapToDouble(DataPoint::latitude).min().orElseThrow();
        double maxLat = dataPoints.stream().mapToDouble(DataPoint::latitude).max().orElseThrow();

        var minLatLon = new LatLon(minLat, minLon);
        var maxLatLon = new LatLon(maxLat, maxLon);

        List<Double> valuesList = new ArrayList<>(dataPoints.stream().map(p -> p.value()).toList());
        var median = calculateMedian(valuesList);

        int gridWidth = (int) Math.max(new LatLon(minLat, minLon).getDistance(new LatLon(minLat, maxLon)),
                new LatLon(maxLat, minLon).getDistance(new LatLon(maxLat, maxLon)));

        gridWidth = (int) (gridWidth / params.cellSize());

        int gridHeight = (int) Math.max(new LatLon(minLat, minLon).getDistance(new LatLon(maxLat, minLon)),
                new LatLon(minLat, maxLon).getDistance(new LatLon(maxLat, maxLon)));

        gridHeight = (int) (gridHeight / params.cellSize());

        if (gridWidth == 0 || gridHeight == 0) {
            return null;
        }

        double lonStep = (maxLon - minLon) / (gridWidth - 1);
        double latStep = (maxLat - minLat) / (gridHeight - 1);

        var grid = new float[gridWidth][gridHeight];

        boolean[][] m = new boolean[gridWidth][gridHeight];
        for (int i = 0; i < gridWidth; i++) {
            for (int j = 0; j < gridHeight; j++) {
                m[i][j] = true;
            }
        }

        Map<String, List<Double>> points = new HashMap<>();
        for (DataPoint point : dataPoints) {
            int xIndex = (int) ((point.longitude() - minLon) / lonStep);
            int yIndex = (int) ((point.latitude() - minLat) / latStep);

            String key = xIndex + "," + yIndex;
            points.computeIfAbsent(key, (k -> new ArrayList<>())).add(point.value());
        }

        var gridBDx = gridWidth / (gridWidth * params.cellSize() / params.blankingDistance());
        var gridBDy = gridHeight / (gridHeight * params.cellSize() / params.blankingDistance());
        var visiblePoints = new boolean[gridWidth][gridHeight];

        for (Map.Entry<String, List<Double>> entry : points.entrySet()) {
            String[] coords = entry.getKey().split(",");
            int xIndex = Integer.parseInt(coords[0]);
            int yIndex = Integer.parseInt(coords[1]);
            double medianValue = calculateMedian(entry.getValue());
            try {
                grid[xIndex][yIndex] = (float) medianValue;
                m[xIndex][yIndex] = false;

                for (int dx = -(int) gridBDx; dx <= gridBDx; dx++) {
                    for (int dy = -(int) gridBDy; dy <= gridBDy; dy++) {
                        int nx = xIndex + dx;
                        int ny = yIndex + dy;
                        if (nx >= 0 && nx < gridWidth && ny >= 0 && ny < gridHeight) {
                            visiblePoints[nx][ny] = true;
                        }
                    }
                }
            } catch (ArrayIndexOutOfBoundsException e) {
                log.info("x = {}, y = {}", xIndex, yIndex);
                log.error("Error", e);
            }
        }

        int count = 0;
        m = thinOutBooleanGrid(m);

        for (int i = 0; i < grid.length; i++) {
            for (int j = 0; j < grid[0].length; j++) {
                if (!m[i][j]) {
                    continue;
                }

                grid[i][j] = (float) median;

                if (!visiblePoints[i][j]) {
                    m[i][j] = false;
                    count++;
                }
            }
        }

        log.info("Filtering complete in {} s", (System.currentTimeMillis() - startFiltering) / 1000);
        log.info("Additional points: {}", count);

        if (Thread.currentThread().isInterrupted()) {
            log.info("Gridding interrupted");
            return null;
        }

        log.info("Splines interpolation");
        var start = System.currentTimeMillis();
        // Use original splines interpolation
        var gridder = new SplinesGridder2();
        var maxIterations = 100;
        var tension = 0f;

        gridder.setMaxIterations(maxIterations); // 200 if the anomaly
        gridder.setTension(tension); //0.9999999f); - maximum
        gridder.gridMissing(m, grid);

        if (Thread.currentThread().isInterrupted()) {
            log.info("Gridding interrupted");
            return null;
        }

        if (gridder.getIterationCount() >= maxIterations) {
            tension = 0.999999f;
            maxIterations = 200;
            gridder.setTension(tension);
            gridder.setMaxIterations(maxIterations);
            gridder.gridMissing(m, grid);
        }
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

    private static List<DataPoint> getMedianValues(List<DataPoint> points) {
        Map<String, List<Double>> dataMap = new HashMap<>();
        for (DataPoint point : points) {
            String key = point.latitude() + "," + point.longitude();
            dataMap.computeIfAbsent(key, k -> new ArrayList<>()).add(point.value());
        }

        List<DataPoint> medianPoints = new ArrayList<>();
        for (Map.Entry<String, List<Double>> entry : dataMap.entrySet()) {
            String[] coords = entry.getKey().split(",");
            double latitude = Double.parseDouble(coords[0]);
            double longitude = Double.parseDouble(coords[1]);
            double medianValue = calculateMedian(entry.getValue());
            medianPoints.add(new DataPoint(latitude, longitude, medianValue));
        }

        return medianPoints;
    }

    private static double calculateMedian(List<Double> values) {
        Collections.sort(values);
        int size = values.size();
        if (size % 2 == 0) {
            return (values.get(size / 2 - 1) + values.get(size / 2)) / 2.0;
        } else {
            return values.get(size / 2);
        }
    }

    /**
     * Thin out the matrix by rows and columns so that the minimum density is not reduced.
     * If almost all cells are filled, the array is returned unchanged.
     */
    public static boolean[][] thinOutBooleanGrid(boolean[][] grid) {
        int rows = grid.length;
        int cols = rows > 0 ? grid[0].length : 0;

        int[] minValues = computeRowColMin(grid);
        int minRowTrue = minValues[0];
        int minColTrue = minValues[1];

        if (minRowTrue >= cols * 0.9 && minColTrue >= rows * 0.9 || minRowTrue == 0 && minColTrue == 0) {
            return grid;
        }

        double avg = Math.min(0.22, Math.min((double) minRowTrue / cols, (double) minColTrue / rows));

        if (avg < 0.05) {
            return grid;
        }

        boolean[][] result = new boolean[rows][cols];
        for (int i = 0; i < rows; i++) {
            System.arraycopy(grid[i], 0, result[i], 0, cols);
        }

        for (int i = 0; i < rows; i++) {
            List<Integer> trueIndices = new ArrayList<>();
            for (int j = 0; j < cols; j++) {
                if (!result[i][j]) {
                    trueIndices.add(j);
                }
            }
            int count = trueIndices.size();
            minRowTrue = (int) (avg * cols);
            if (count > minRowTrue && minRowTrue > 0) {
                List<Integer> keepIndices = new ArrayList<>();
                double step = (double) (count - 1) / (minRowTrue - 1);
                for (int k = 0; k < minRowTrue; k++) {
                    int index = trueIndices.get((int) Math.round(k * step));
                    keepIndices.add(index);
                }
                for (int j = 0; j < cols; j++) {
                    result[i][j] = true;
                }
                for (int j : keepIndices) {
                    result[i][j] = false;
                }
            }
        }

        for (int j = 0; j < cols; j++) {
            List<Integer> trueIndices = new ArrayList<>();
            for (int i = 0; i < rows; i++) {
                if (!result[i][j]) {
                    trueIndices.add(i);
                }
            }
            int count = trueIndices.size();
            minColTrue = (int) (avg * rows);
            if (count > minColTrue && minColTrue > 0) {
                List<Integer> keepIndices = new ArrayList<>();
                double step = (double) (count - 1) / (minColTrue - 1);
                for (int k = 0; k < minColTrue; k++) {
                    int index = trueIndices.get((int) Math.round(k * step));
                    keepIndices.add(index);
                }
                for (int i = 0; i < rows; i++) {
                    result[i][j] = true;
                }
                for (int i : keepIndices) {
                    result[i][j] = false;
                }
            }
        }
        return result;
    }

    /**
     * Before thinning, determine the minimum number of true values per row and column.
     */
    private static int[] computeRowColMin(boolean[][] grid) {
        int rows = grid.length;
        int cols = rows > 0 ? grid[0].length : 0;
        int[] rowCounts = new int[rows];
        int[] colCounts = new int[cols];

        for (int i = 0; i < rows; i++) {
            int countRow = 0;
            for (int j = 0; j < cols; j++) {
                if (!grid[i][j]) {
                    countRow++;
                    colCounts[j]++;
                }
            }
            rowCounts[i] = countRow;
        }
        int rowsum = 0;
        int rowcount = 0;
        for (int i = 0; i < rows; i++) {
            if (rowCounts[i] > cols * 0.01) {
                rowsum += rowCounts[i];
                rowcount++;
            }
        }

        int colsum = 0;
        int colcount = 0;
        for (int j = 0; j < cols; j++) {
            if (colCounts[j] > rows * 0.01) {
                colsum += colCounts[j];
                colcount++;
            }
        }
        return new int[]{rowsum / (rowcount != 0 ? rowcount : 1), colsum / (colcount != 0 ? colcount : 1)};
    }
}
