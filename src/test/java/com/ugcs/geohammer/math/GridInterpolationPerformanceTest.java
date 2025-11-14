package com.ugcs.geohammer.math;

import com.ugcs.geohammer.model.DataPoint;
import edu.mines.jtk.interp.SplinesGridder2;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.index.kdtree.KdTree;
import static org.junit.jupiter.api.Assertions.*;

import java.io.PrintWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GridInterpolationPerformanceTest {

    private static final StringBuilder logBuffer = new StringBuilder();
    private static final String LOG_FILE = "interpolation_performance_test.log";
    private static PrintWriter logWriter;

    private void log(String message) {
        String logMessage = "[DEBUG_LOG] " + message;
        // Write to console
        System.out.println(logMessage);
        System.out.flush();
        // Write to buffer
        logBuffer.append(logMessage).append("\n");
        // Write to file
        if (logWriter != null) {
            logWriter.println(logMessage);
            logWriter.flush();
        }
    }

    private void logSection(String title) {
        log("\n========== " + title + " ==========");
        log("================================================");
    }

    @BeforeAll
    void setup() throws IOException {
        results.clear();
        logBuffer.setLength(0);
        logWriter = new PrintWriter(new FileWriter(LOG_FILE));
        logSection("Starting Interpolation Performance Tests");
    }
    private static class PerformanceResult {
        final int numPoints;
        final int gridSize;
        final double idwThroughput;
        final double splinesThroughput;
        final double idwTime;
        final double splinesTime;
        final double setupTime;
        final double memoryUsage;

        PerformanceResult(int numPoints, int gridSize, double idwTime, double splinesTime, 
                         double setupTime, double memoryUsage) {
            this.numPoints = numPoints;
            this.gridSize = gridSize;
            this.idwTime = idwTime;
            this.splinesTime = splinesTime;
            this.setupTime = setupTime;
            this.memoryUsage = memoryUsage;
            this.idwThroughput = numPoints / (idwTime / 1_000_000.0);
            this.splinesThroughput = numPoints / (splinesTime / 1_000_000.0);
        }
    }

    private static final List<PerformanceResult> results = new ArrayList<>();

    // Test area centered around 0,0 with size that keeps coordinates in valid ranges
    private static final double TEST_AREA_SIZE = 10.0; // degrees
    private static final double CENTER_LAT = 0.0;
    private static final double CENTER_LON = 0.0;
    private static final Random random = new Random(42); // Fixed seed for reproducibility

    /**
     * Generates test data points with random distribution within valid lat/lon ranges
     */
    private List<DataPoint> generateTestData(int numPoints) {
        List<DataPoint> points = new ArrayList<>();
        for (int i = 0; i < numPoints; i++) {
            // Generate points in valid lat/lon ranges centered around CENTER_LAT/LON
            double lat = CENTER_LAT + (random.nextDouble() - 0.5) * TEST_AREA_SIZE;
            double lon = CENTER_LON + (random.nextDouble() - 0.5) * TEST_AREA_SIZE;
            double value = random.nextDouble() * 100; // Random values between 0 and 100
            points.add(new DataPoint(lat, lon, value));
        }
        return points;
    }

    /**
     * Creates a KD-tree from test points
     */
    private KdTree buildKdTree(List<DataPoint> points) {
        KdTree kdTree = new KdTree();
        for (DataPoint point : points) {
            kdTree.insert(new Coordinate(point.longitude(), point.latitude()), point);
        }
        return kdTree;
    }

    private static class GridData {
        float[][] grid;
        boolean[][] missing;

        GridData(float[][] grid, boolean[][] missing) {
            this.grid = grid;
            this.missing = missing;
        }
    }

    /**
     * Creates a grid with given size and marks missing values
     */
    private GridData createGrid(int gridSize, List<DataPoint> points) {
        float[][] grid = new float[gridSize][gridSize];
        boolean[][] missing = new boolean[gridSize][gridSize];

        // Initialize all as missing
        for (int i = 0; i < gridSize; i++) {
            for (int j = 0; j < gridSize; j++) {
                missing[i][j] = true;
            }
        }

        // Calculate grid indices based on lat/lon ranges
        double minLat = CENTER_LAT - TEST_AREA_SIZE/2;
        double maxLat = CENTER_LAT + TEST_AREA_SIZE/2;
        double minLon = CENTER_LON - TEST_AREA_SIZE/2;
        double maxLon = CENTER_LON + TEST_AREA_SIZE/2;

        // Place known points
        for (DataPoint point : points) {
            int x = (int) ((point.longitude() - minLon) / TEST_AREA_SIZE * (gridSize - 1));
            int y = (int) ((point.latitude() - minLat) / TEST_AREA_SIZE * (gridSize - 1));
            if (x >= 0 && x < gridSize && y >= 0 && y < gridSize) {
                grid[x][y] = (float) point.value();
                missing[x][y] = false;
            }
        }

        return new GridData(grid, missing);
    }

    @Test
    void testInterpolationPerformanceSmallDataset() throws InterruptedException {
        logSection("INTERPOLATION PERFORMANCE TEST");
        log("Test Case: Small Dataset");
        runPerformanceTest(100, 50); // 100 points, 50x50 grid
    }

    @Test
    void testInterpolationPerformanceMediumDataset() throws InterruptedException {
        logSection("INTERPOLATION PERFORMANCE TEST");
        log("Test Case: Medium Dataset");
        runPerformanceTest(1000, 100); // 1000 points, 100x100 grid
    }

    @Test
    void testInterpolationPerformanceLargeDataset() throws InterruptedException {
        logSection("INTERPOLATION PERFORMANCE TEST");
        log("Test Case: Large Dataset");
        runPerformanceTest(10000, 200); // 10000 points, 200x200 grid
    }

    @AfterAll
    void printSummary() throws IOException {
        logSection("PERFORMANCE SUMMARY");
        log("Comparing performance across dataset sizes:\n");
        log("Dataset Size | Grid Size | IDW (pts/ms) | Splines (pts/ms) | Memory (MB) | Setup % | IDW:Splines");
        log("-------------|------------|--------------|-----------------|-------------|----------|------------");

        for (PerformanceResult result : results) {
            double setupPercent = result.setupTime * 100.0 / (result.setupTime + result.idwTime + result.splinesTime);
            log(String.format("%11d | %9d | %11.2f | %14.2f | %10.1f | %7.1f%% | %9.2f",
                result.numPoints,
                result.gridSize,
                result.idwThroughput,
                result.splinesThroughput,
                result.memoryUsage,
                setupPercent,
                result.idwTime / result.splinesTime
            ));
        }

        // Calculate averages
        double avgIdwThroughput = results.stream().mapToDouble(r -> r.idwThroughput).average().orElse(0);
        double avgSplinesThroughput = results.stream().mapToDouble(r -> r.splinesThroughput).average().orElse(0);
        double avgMemory = results.stream().mapToDouble(r -> r.memoryUsage).average().orElse(0);
        double avgSetupPercent = results.stream()
            .mapToDouble(r -> r.setupTime * 100.0 / (r.setupTime + r.idwTime + r.splinesTime))
            .average().orElse(0);
        double avgRatio = results.stream().mapToDouble(r -> r.idwTime / r.splinesTime).average().orElse(0);

        log("-------------|------------|--------------|-----------------|-------------|----------|------------");
        log(String.format("%11s | %9s | %11.2f | %14.2f | %10.1f | %7.1f%% | %9.2f",
            "AVERAGE", "-", avgIdwThroughput, avgSplinesThroughput, avgMemory, avgSetupPercent, avgRatio));
        log("================================================\n");

        if (logWriter != null) {
            logWriter.close();
            log("Performance test results have been written to: " + LOG_FILE);
        }
    }

    private void runPerformanceTest(int numPoints, int gridSize) throws InterruptedException {
        // Generate test data
        long startGen = System.nanoTime();
        List<DataPoint> points = generateTestData(numPoints);
        long genTime = System.nanoTime() - startGen;
        System.out.println("[DEBUG_LOG] Data generation time: " + genTime / 1_000_000.0 + " ms");

        // Build KD-tree
        long startKd = System.nanoTime();
        KdTree kdTree = buildKdTree(points);
        long kdTime = System.nanoTime() - startKd;
        System.out.println("[DEBUG_LOG] KD-tree build time: " + kdTime / 1_000_000.0 + " ms");

        // Create grid
        long startGrid = System.nanoTime();
        GridData gridData = createGrid(gridSize, points);
        long gridTime = System.nanoTime() - startGrid;
        System.out.println("[DEBUG_LOG] Grid creation time: " + gridTime / 1_000_000.0 + " ms");

        // Calculate coordinate ranges
        double minLat = CENTER_LAT - TEST_AREA_SIZE/2;
        double maxLat = CENTER_LAT + TEST_AREA_SIZE/2;
        double minLon = CENTER_LON - TEST_AREA_SIZE/2;
        double maxLon = CENTER_LON + TEST_AREA_SIZE/2;

        // Test IDW interpolation
        long startIdw = System.nanoTime();
        IDWInterpolator idwInterpolator = new IDWInterpolator(
            kdTree,
            2.0,  // power
            4,    // minPoints
            TEST_AREA_SIZE / gridSize * 5, // maxSearchRadius
            TEST_AREA_SIZE / gridSize      // initialSearchRadius
        );

        float[][] gridIdw = gridData.grid.clone();
        for (int i = 0; i < gridSize; i++) {
            for (int j = 0; j < gridSize; j++) {
                if (gridData.missing[i][j]) {
                    // Convert grid indices back to lat/lon coordinates
                    double lon = minLon + (i * TEST_AREA_SIZE / (gridSize - 1));
                    double lat = minLat + (j * TEST_AREA_SIZE / (gridSize - 1));
                    gridIdw[i][j] = (float) idwInterpolator.interpolate(lon, lat);
                }
            }
        }
        long idwTime = System.nanoTime() - startIdw;
        System.out.println("[DEBUG_LOG] IDW interpolation time: " + idwTime / 1_000_000.0 + " ms");

        // Test Splines interpolation
        float[][] gridSplines = gridData.grid.clone();
        long startSplines = System.nanoTime();
        SplinesGridder2 gridder = new SplinesGridder2();
        gridder.gridMissing(gridData.missing, gridSplines);
        long splinesTime = System.nanoTime() - startSplines;
        System.out.println("[DEBUG_LOG] Splines interpolation time: " + splinesTime / 1_000_000.0 + " ms");

        // Verify results are reasonable
        assertFalse(hasNaNValues(gridIdw), "IDW interpolation should not produce NaN values");
        assertFalse(hasNaNValues(gridSplines), "Splines interpolation should not produce NaN values");

        // Calculate performance metrics
        double idwPointsPerMs = numPoints / (idwTime / 1_000_000.0);
        double splinesPointsPerMs = numPoints / (splinesTime / 1_000_000.0);
        double setupTimeTotal = genTime + kdTime + gridTime;
        double interpolationTimeTotal = idwTime + splinesTime;

        // Verify reasonable performance characteristics
        assertTrue(genTime < interpolationTimeTotal, 
            "Data generation should be faster than total interpolation time");
        assertTrue(idwTime < splinesTime, 
            "IDW should be faster than Splines interpolation");

        // Force GC to get more accurate memory measurements
        System.gc();
        Thread.sleep(100); // Give GC time to complete

        log("Performance metrics:");
        log(String.format("Dataset size: %d points, Grid size: %dx%d", numPoints, gridSize, gridSize));
        log(String.format("Memory usage: %.1f MB", 
            (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024.0 * 1024.0)));
        log("Timing breakdown:");
        log(String.format("  - Data generation: %.2f ms", genTime / 1_000_000.0));
        log(String.format("  - KD-tree build: %.2f ms", kdTime / 1_000_000.0));
        log(String.format("  - Grid creation: %.2f ms", gridTime / 1_000_000.0));
        log(String.format("  - IDW interpolation: %.2f ms", idwTime / 1_000_000.0));
        log(String.format("  - Splines interpolation: %.2f ms", splinesTime / 1_000_000.0));
        log("Performance rates:");
        log(String.format("  - IDW: %.2f points/ms", idwPointsPerMs));
        log(String.format("  - Splines: %.2f points/ms", splinesPointsPerMs));
        log("Comparison:");
        log(String.format("  - IDW/Splines time ratio: %.2f", idwTime / (double)splinesTime));

        // Performance threshold assertions
        double minIdwThroughput = 1.0;  // points/ms
        double minSplinesThroughput = 0.1;  // points/ms

        assertTrue(idwPointsPerMs > minIdwThroughput, 
            String.format("IDW throughput (%.2f points/ms) should be above %.2f points/ms", 
                idwPointsPerMs, minIdwThroughput));
        assertTrue(splinesPointsPerMs > minSplinesThroughput, 
            String.format("Splines throughput (%.2f points/ms) should be above %.2f points/ms", 
                splinesPointsPerMs, minSplinesThroughput));

        // Print setup vs interpolation time ratio
        System.out.println("[DEBUG_LOG] Time distribution:");
        System.out.println("[DEBUG_LOG]   - Setup time: " + String.format("%.2f", setupTimeTotal / 1_000_000.0) + " ms (" + 
            String.format("%.1f", setupTimeTotal * 100.0 / (setupTimeTotal + interpolationTimeTotal)) + "%)");
        System.out.println("[DEBUG_LOG]   - Interpolation time: " + String.format("%.2f", interpolationTimeTotal / 1_000_000.0) + " ms (" + 
            String.format("%.1f", interpolationTimeTotal * 100.0 / (setupTimeTotal + interpolationTimeTotal)) + "%)");

        // Store results for summary
        results.add(new PerformanceResult(
            numPoints, 
            gridSize,
            idwTime,
            splinesTime,
            setupTimeTotal,
            (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024.0 * 1024.0)
        ));
    }

    private boolean hasNaNValues(float[][] grid) {
        for (float[] row : grid) {
            for (float value : row) {
                if (Float.isNaN(value)) {
                    return true;
                }
            }
        }
        return false;
    }
}
