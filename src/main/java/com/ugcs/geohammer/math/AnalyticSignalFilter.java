package com.ugcs.geohammer.math;

import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.util.Check;
import edu.emory.mathcs.jtransforms.fft.FloatFFT_2D;

import java.util.Arrays;

public class AnalyticSignalFilter {

    // i grows east along x, j grows north along y
    private final float[][] grid;

    private final float[][] gridSource;

    private final int m;

    private final int n;

    private final double cellWidth;

    private final double cellHeight;

    public AnalyticSignalFilter(float[][] grid, double cellWidth, double cellHeight) {
        Check.notNull(grid);

        this.m = grid.length;
        this.n = m > 0 ? grid[0].length : 0;
        // copy within given range
        this.grid = copy(grid);
        this.gridSource = grid;

        this.cellWidth = cellWidth;
        this.cellHeight = cellHeight;

        // interpolate
        GridInterpolator.interpolate(this.grid, cellWidth, cellHeight);
    }

    public AnalyticSignalFilter(float[][] grid, LatLon min, LatLon max) {
        this(grid,
                getCellWidth(grid, min, max),
                getCellHeight(grid, min, max));
    }

    private static float[][] copy(float[][] grid) {
        float[][] copy = new float[grid.length][];
        for (int i = 0; i < grid.length; i++) {
            copy[i] = Arrays.copyOf(grid[i], grid[i].length);
        }
        return copy;
    }

    // analytic signal does not depend on the base level of the field, without it
    // float values keep the precision the derivatives need
    public static float[][] center(float[][] grid) {
        double sum = 0;
        int count = 0;
        for (float[] row : grid) {
            for (float value : row) {
                if (!Float.isNaN(value)) {
                    sum += value;
                    count++;
                }
            }
        }
        double mean = count > 0 ? sum / count : 0;

        float[][] centered = new float[grid.length][];
        for (int i = 0; i < grid.length; i++) {
            centered[i] = new float[grid[i].length];
            for (int j = 0; j < grid[i].length; j++) {
                centered[i][j] = (float) (grid[i][j] - mean);
            }
        }
        return centered;
    }

    public static double getCellWidth(float[][] grid, LatLon min, LatLon max) {
        int gridWidth = grid.length;
        if (gridWidth == 0) {
            return 0.0;
        }
        double longitudeStep = (max.getLonDgr() - min.getLonDgr()) / gridWidth;
        double centerLatitude = (min.getLatDgr() + max.getLatDgr()) / 2;
        return CoordinatesMath.measure(centerLatitude, 0, centerLatitude, longitudeStep);
    }

    public static double getCellHeight(float[][] grid, LatLon min, LatLon max) {
        int gridHeight = grid.length > 0 ? grid[0].length : 0;
        if (gridHeight == 0) {
            return 0.0;
        }
        double latitudeStep = (max.getLatDgr() - min.getLatDgr()) / gridHeight;
        return CoordinatesMath.measure(0, 0, latitudeStep, 0);
    }

    public AnalyticSignal evaluate() {
        float[][] dz = getZDerivativeMatrix();

        float[][] magnitudes = new float[m][n];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                float magnitude = Float.NaN;
                if (!Float.isNaN(gridSource[i][j])) {
                    float dx = getXDerivative(i, j);
                    float dy = getYDerivative(i, j);
                    magnitude = (float) Math.sqrt(dx * dx + dy * dy + dz[i][j] * dz[i][j]);
                }
                magnitudes[i][j] = magnitude;
            }
        }

        return new AnalyticSignal(magnitudes);
    }

    // x-direction derivative
    private float getXDerivative(int i, int j) {
        if (i > 1 && i < m - 2) {
            // 5-point stencil
            return (float) ((-grid[i + 2][j] + 8.0 * grid[i + 1][j] - 8.0 * grid[i - 1][j] + grid[i - 2][j])
                    / (12.0 * cellWidth));
        } else if (i > 0 && i < m - 1) {
            // 3-point central difference
            return (float) ((grid[i + 1][j] - grid[i - 1][j])
                    / (2.0 * cellWidth));
        } else if (i == 0 && i < m - 1) {
            // forward difference
            return (float) ((grid[i + 1][j] - grid[i][j])
                    / cellWidth);
        } else if (i == m - 1 && i > 0) {
            // backward difference
            return (float) ((grid[i][j] - grid[i - 1][j])
                    / cellWidth);
        }

        return Float.NaN;
    }

    // y-direction derivative
    private float getYDerivative(int i, int j) {
        if (j > 1 && j < n - 2) {
            // 5-point stencil
            return (float) ((-grid[i][j + 2] + 8.0 * grid[i][j + 1] - 8.0 * grid[i][j - 1] + grid[i][j - 2])
                    / (12.0 * cellHeight));
        } else if (j > 0 && j < n - 1) {
            // 3-point central difference
            return (float) ((grid[i][j + 1] - grid[i][j - 1])
                    / (2.0 * cellHeight));
        } else if (j == 0 && j < n - 1) {
            // forward difference
            return (float) ((grid[i][j + 1] - grid[i][j])
                    / cellHeight);
        } else if (j == n - 1 && j > 0) {
            // backward difference
            return (float) ((grid[i][j] - grid[i][j - 1])
                    / cellHeight);
        }

        return Float.NaN;
    }

    private float[][] getZDerivativeMatrix() {
        FloatFFT_2D fft2 = new FloatFFT_2D(m, n);

        // copy grid into FFT input array (real, imaginary = 0)
        float[][] complexGrid = new float[m][2 * n];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                complexGrid[i][2 * j] = grid[i][j];
            }
        }

        // forward FFT
        fft2.complexForward(complexGrid);

        // frequency steps
        double dkx = 2.0 * Math.PI / (m * cellWidth);
        double dky = 2.0 * Math.PI / (n * cellHeight);

        // multiply by |k| in frequency domain
        for (int i = 0; i < m; i++) {
            int kxIndex = (i <= m / 2) ? i : i - m; // FFT ordering
            double kx = kxIndex * dkx;
            for (int j = 0; j < n; j++) {
                int kyIndex = (j <= n / 2) ? j : j - n;
                double ky = kyIndex * dky;
                double k = Math.sqrt(kx * kx + ky * ky);

                complexGrid[i][2 * j] *= k;
                complexGrid[i][2 * j + 1] *= k;
            }
        }

        // inverse FFT, scaled
        fft2.complexInverse(complexGrid, true);

        // take real part as vertical derivative
        float[][] dz = new float[m][n];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                dz[i][j] = complexGrid[i][2 * j];
            }
        }
        return dz;
    }
}