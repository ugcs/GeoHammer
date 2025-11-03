package com.ugcs.gprvisualizer.app.service;

import com.github.thecoldwine.sigrun.common.ext.CsvFile;
import com.github.thecoldwine.sigrun.common.ext.LatLon;
import com.ugcs.gprvisualizer.app.OptionPane;
import com.ugcs.gprvisualizer.event.GriddingParamsSetted;
import com.ugcs.gprvisualizer.gpr.Model;
import com.ugcs.gprvisualizer.math.DataPoint;
import com.ugcs.gprvisualizer.utils.Check;
import edu.mines.jtk.interp.SplinesGridder2;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GriddingService {

    private final Model model;

    public GriddingService(Model model) {
        Check.notNull(model);

        this.model = model;
    }

    public Map<File, GriddingResult> runGridding(CsvFile csvFile, String seriesName,
            GriddingParamsSetted params, OptionPane.GriddingRange range) {
        Check.notNull(csvFile);
        Check.notNull(seriesName);

        var startFiltering = System.currentTimeMillis();

        Set<CsvFile> targetFiles = params.isToAll()
                ? model.getFileManager().getCsvFiles()
                .stream()
                .filter(f -> f.isSameTemplate(csvFile))
                .collect(Collectors.toSet())
                : Collections.singleton(csvFile);

        List<DataPoint> dataPoints = new ArrayList<>();
        for (CsvFile targetFile : targetFiles) {
            dataPoints.addAll(getDataPoints(targetFile, seriesName));
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

        int gridSizeX = (int) Math.max(new LatLon(minLat, minLon).getDistance(new LatLon(minLat, maxLon)),
                new LatLon(maxLat, minLon).getDistance(new LatLon(maxLat, maxLon)));

        gridSizeX = (int) (gridSizeX / params.getCellSize());

        int gridSizeY = (int) Math.max(new LatLon(minLat, minLon).getDistance(new LatLon(maxLat, minLon)),
                new LatLon(minLat, maxLon).getDistance(new LatLon(maxLat, maxLon)));

        gridSizeY = (int) (gridSizeY / params.getCellSize());

        double lonStep = (maxLon - minLon) / gridSizeX;
        double latStep = (maxLat - minLat) / gridSizeY;

        var gridData = new float[gridSizeX][gridSizeY];

        boolean[][] m = new boolean[gridSizeX][gridSizeY];
        for (int i = 0; i < gridSizeX; i++) {
            for (int j = 0; j < gridSizeY; j++) {
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

        var gridBDx = gridSizeX / (gridSizeX * params.getCellSize() / params.getBlankingDistance());
        var gridBDy = gridSizeY / (gridSizeY * params.getCellSize() / params.getBlankingDistance());
        var visiblePoints = new boolean[gridSizeX][gridSizeY];

        for (Map.Entry<String, List<Double>> entry : points.entrySet()) {
            String[] coords = entry.getKey().split(",");
            int xIndex = Integer.parseInt(coords[0]);
            int yIndex = Integer.parseInt(coords[1]);
            double medianValue = calculateMedian(entry.getValue());
            try {
                gridData[xIndex][yIndex] = (float) medianValue;
                m[xIndex][yIndex] = false;

                for (int dx = -(int) gridBDx; dx <= gridBDx; dx++) {
                    for (int dy = -(int) gridBDy; dy <= gridBDy; dy++) {
                        int nx = xIndex + dx;
                        int ny = yIndex + dy;
                        if (nx >= 0 && nx < gridSizeX && ny >= 0 && ny < gridSizeY) {
                            visiblePoints[nx][ny] = true;
                        }
                    }
                }
            } catch (ArrayIndexOutOfBoundsException e) {
                System.out.println("Out of bounds - xIndex = " + xIndex + " yIndex = " + yIndex);
            }
        }

        int count = 0;
        m = thinOutBooleanGrid(m);

        for (int i = 0; i < gridData.length; i++) {
            for (int j = 0; j < gridData[0].length; j++) {
                if (!m[i][j]) {
                    continue;
                }

                gridData[i][j] = (float) median;

                if (!visiblePoints[i][j]) {
                    m[i][j] = false;
                    count++;
                }
            }
        }

        System.out.println("Filtering complete in " + (System.currentTimeMillis() - startFiltering) / 1000 + "s");
        System.out.println("Aditional points: " + count);

        if (Thread.currentThread().isInterrupted()) {
            System.out.println("Gridding interrupted");
            return null;
        }

        System.out.println("Splines interpolation");
        var start = System.currentTimeMillis();
        // Use original splines interpolation
        var gridder = new SplinesGridder2();
        var maxIterations = 100;
        var tension = 0f;

        gridder.setMaxIterations(maxIterations); // 200 if the anomaly
        gridder.setTension(tension); //0.9999999f); - maximum
        gridder.gridMissing(m, gridData);

        if (Thread.currentThread().isInterrupted()) {
            System.out.println("Gridding interrupted");
            return null;
        }

        if (gridder.getIterationCount() >= maxIterations) {
            tension = 0.999999f;
            maxIterations = 200;
            gridder.setTension(tension);
            gridder.setMaxIterations(maxIterations);
            gridder.gridMissing(m, gridData);
        }
        System.out.println("Iterations: " + gridder.getIterationCount()
                + " time: " + (System.currentTimeMillis() - start) / 1000 + "s"
                + " tension: " + tension
                + " maxIterations: " + maxIterations);

        System.out.println("Interpolation complete");

        if (Thread.currentThread().isInterrupted()) {
            System.out.println("Gridding interrupted");
            return null;
        }

        for (int i = 0; i < gridData.length; i++) {
            for (int j = 0; j < gridData[0].length; j++) {
                if (!visiblePoints[i][j]) {
                    gridData[i][j] = Float.NaN;
                }
            }
        }

        // Store the gridding result for the affected files
        GriddingResult result = new GriddingResult(
                gridData, // Deep cloning is done in the constructor
                applyLowPassFilter(gridData),
                minLatLon,
                maxLatLon,
                params.getCellSize(),
                params.getBlankingDistance(),
                (float) range.lowValue(),
                (float) range.highValue(),
                seriesName,
                params.isAnalyticSignalEnabled(),
                params.isHillShadingEnabled(),
                params.isSmoothingEnabled(),
                params.getHillShadingAzimuth(),
                params.getHillShadingAltitude(),
                params.getHillShadingIntensity()
        );

        HashMap<File, GriddingResult> results = new HashMap<>();
        for (CsvFile targetFile : targetFiles) {
            results.put(targetFile.getFile(), result);
        }
        return results;
    }

    private List<DataPoint> getDataPoints(CsvFile csvFile, String seriesName) {
        return csvFile.getGeoData().stream()
                .filter(gd -> gd.getNumber(seriesName) != null)
                .map(gd -> new DataPoint(gd.getLatitude(), gd.getLongitude(), gd.getNumber(seriesName).doubleValue()))
                .toList();
    }

    private static List<DataPoint> getMedianValues(List<DataPoint> dataPoints) {
        Map<String, List<Double>> dataMap = new HashMap<>();
        for (DataPoint point : dataPoints) {
            String key = point.latitude() + "," + point.longitude();
            dataMap.computeIfAbsent(key, k -> new ArrayList<>()).add(point.value());
        }

        List<DataPoint> medianDataPoints = new ArrayList<>();
        for (Map.Entry<String, List<Double>> entry : dataMap.entrySet()) {
            String[] coords = entry.getKey().split(",");
            double latitude = Double.parseDouble(coords[0]);
            double longitude = Double.parseDouble(coords[1]);
            double medianValue = calculateMedian(entry.getValue());
            medianDataPoints.add(new DataPoint(latitude, longitude, medianValue));
        }

        return medianDataPoints;
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
     * Applies a low-pass filter to the grid data to smooth out high-frequency variations.
     * Uses a Gaussian kernel for the convolution.
     *
     * @param gridData The grid data to filter
     */
    private float[][] applyLowPassFilter(float[][] gridData) {
        if (gridData == null || gridData.length == 0 || gridData[0].length == 0) {
            return gridData;
        }

        int kernelSize = 15;
        int kernelRadius = 7;

        System.out.println("Applying low-pass filter with " + kernelSize + "x" + kernelSize + " kernel");
        long startTime = System.currentTimeMillis();

        float[][] kernel = new float[kernelSize][kernelSize];

        // Initialize kernel with Gaussian values
        double sigma = 5.0;
        double sum = 0.0;

        for (int x = -kernelRadius; x <= kernelRadius; x++) {
            for (int y = -kernelRadius; y <= kernelRadius; y++) {
                double value = Math.exp(-(x * x + y * y) / (2 * sigma * sigma));
                kernel[x + kernelRadius][y + kernelRadius] = (float) value;
                sum += value;
            }
        }

        // Normalize kernel
        for (int i = 0; i < kernelSize; i++) {
            for (int j = 0; j < kernelSize; j++) {
                kernel[i][j] /= sum;
            }
        }

        int width = gridData.length;
        int height = gridData[0].length;

        var resultData = cloneGridData(gridData);

        // Apply convolution
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                // Skip NaN values
                if (Float.isNaN(gridData[i][j])) {
                    continue;
                }

                float sum2 = 0;
                float weightSum = 0;

                // Apply kernel
                for (int ki = -kernelRadius; ki <= kernelRadius; ki++) {
                    for (int kj = -kernelRadius; kj <= kernelRadius; kj++) {
                        int ni = i + ki;
                        int nj = j + kj;

                        // Skip out of bounds or NaN values
                        if (ni < 0 || ni >= width || nj < 0 || nj >= height || Float.isNaN(gridData[ni][nj])) {
                            continue;
                        }

                        float weight = kernel[ki + kernelRadius][kj + kernelRadius];
                        sum2 += gridData[ni][nj] * weight;
                        weightSum += weight;
                    }
                }

                // Normalize by the sum of weights
                if (weightSum > 0) {
                    resultData[i][j] = sum2 / weightSum;
                }
            }
        }

        System.out.println("Low-pass filter applied in " + (System.currentTimeMillis() - startTime) + "ms");
        return resultData;
    }

    private static float[][] cloneGridData(float[][] gridData) {
        var result = new float[gridData.length][];
        for (int i = 0; i < gridData.length; i++) {
            result[i] = gridData[i].clone();
        }
        return result;
    }

    /**
     * Thin out the matrix by rows and columns so that the minimum density is not reduced.
     * If almost all cells are filled, the array is returned unchanged.
     */
    public static boolean[][] thinOutBooleanGrid(boolean[][] gridData) {
        int rows = gridData.length;
        int cols = rows > 0 ? gridData[0].length : 0;

        int[] minValues = computeRowColMin(gridData);
        int minRowTrue = minValues[0];
        int minColTrue = minValues[1];

        if (minRowTrue >= cols * 0.9 && minColTrue >= rows * 0.9 || minRowTrue == 0 && minColTrue == 0) {
            return gridData;
        }

        double avg = Math.min(0.22, Math.min((double) minRowTrue / cols, (double) minColTrue / rows));

        if (avg < 0.05) {
            return gridData;
        }

        boolean[][] result = new boolean[rows][cols];
        for (int i = 0; i < rows; i++) {
            System.arraycopy(gridData[i], 0, result[i], 0, cols);
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
    private static int[] computeRowColMin(boolean[][] gridData) {
        int rows = gridData.length;
        int cols = rows > 0 ? gridData[0].length : 0;
        int[] rowCounts = new int[rows];
        int[] colCounts = new int[cols];

        for (int i = 0; i < rows; i++) {
            int countRow = 0;
            for (int j = 0; j < cols; j++) {
                if (!gridData[i][j]) {
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
