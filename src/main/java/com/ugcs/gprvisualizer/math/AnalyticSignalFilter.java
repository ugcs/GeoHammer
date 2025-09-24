package com.ugcs.gprvisualizer.math;

import com.ugcs.gprvisualizer.utils.Check;
import edu.emory.mathcs.jtransforms.fft.FloatFFT_2D;

import java.util.Arrays;

public class AnalyticSignalFilter {

    private final float[][] grid;
    private final float[][] gridOrigin;
    private final int m;
    private final int n;

    private final double cellWidth;
    private final double cellHeight;

    public AnalyticSignalFilter(float[][] grid, double cellWidth, double cellHeight) {
        Check.notNull(grid);

        this.m = grid.length;
        this.n = grid[0].length;
        // copy within given range
        this.grid = copy(grid);
        this.gridOrigin = grid;

        this.cellWidth = cellWidth;
        this.cellHeight = cellHeight;

        // interpolate
        GridInterpolator.interpolate(this.grid, cellWidth, cellHeight);
    }

    private static float[][] copy(float[][] grid) {
        float[][] copy = new float[grid.length][];
        for (int i = 0; i < grid.length; i++) {
            copy[i] = Arrays.copyOf(grid[i], grid[i].length);
        }
        return copy;
    }

    public AnalyticSignal evaluate() {
        float[][] dz = getZDerivativeMatrix();

        float[][] magnitudes = new float[m][n];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                float magnitude = Float.NaN;
                if (!Float.isNaN(gridOrigin[i][j])) {
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
        if (j > 1 && j < n - 2
                && !Float.isNaN(grid[i][j - 2])
                && !Float.isNaN(grid[i][j - 1])
                && !Float.isNaN(grid[i][j + 1])
                && !Float.isNaN(grid[i][j + 2])) {
            // 5-point stencil
            return (float) ((-grid[i][j + 2] + 8.0 * grid[i][j + 1] - 8.0 * grid[i][j - 1] + grid[i][j - 2])
                    / (12.0 * cellWidth));
        } else if (j > 0 && j < n - 1
                && !Float.isNaN(grid[i][j - 1])
                && !Float.isNaN(grid[i][j + 1])) {
            // 3-point central difference
            return (float) ((grid[i][j + 1] - grid[i][j - 1])
                    / (2.0 * cellWidth));
        } else if (j == 0 && j < n - 1
                && !Float.isNaN(grid[i][j + 1])
                && !Float.isNaN(grid[i][j])) {
            // forward difference
            return (float) ((grid[i][j + 1] - grid[i][j])
                    / cellWidth);
        } else if (j == n - 1 && j > 0
                && !Float.isNaN(grid[i][j - 1])
                && !Float.isNaN(grid[i][j])) {
            // backward difference
            return (float) ((grid[i][j] - grid[i][j - 1])
                    / cellWidth);
        }

        return Float.NaN;
    }

    // y-direction derivative
    private float getYDerivative(int i, int j) {
        if (i > 1 && i < m - 2
                && !Float.isNaN(grid[i - 2][j])
                && !Float.isNaN(grid[i - 1][j])
                && !Float.isNaN(grid[i + 1][j])
                && !Float.isNaN(grid[i + 2][j])) {
            // 5-point stencil
            return (float) ((-grid[i + 2][j] + 8.0 * grid[i + 1][j] - 8.0 * grid[i - 1][j] + grid[i - 2][j])
                    / (12.0 * cellHeight));
        } else if (i > 0 && i < m - 1
                && !Float.isNaN(grid[i - 1][j])
                && !Float.isNaN(grid[i + 1][j])) {
            // 3-point central difference
            return (float) ((grid[i + 1][j] - grid[i - 1][j])
                    / (2.0 * cellHeight));
        } else if (i == 0 && i < m - 1
                && !Float.isNaN(grid[i + 1][j])
                && !Float.isNaN(grid[i][j])) {
            // forward difference
            return (float) ((grid[i + 1][j] - grid[i][j])
                    / cellHeight);
        } else if (i == m - 1 && i > 0
                && !Float.isNaN(grid[i - 1][j])
                && !Float.isNaN(grid[i][j])) {
            // backward difference
            return (float) ((grid[i][j] - grid[i - 1][j])
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
                float v = grid[i][j];
                if (!Float.isNaN(v)) {
                    complexGrid[i][2 * j] = v;
                }
            }
        }

        // forward FFT
        fft2.complexForward(complexGrid);

        // frequency steps
        double dkx = 2.0 * Math.PI / (n * cellWidth);
        double dky = 2.0 * Math.PI / (m * cellHeight);

        // multiply by |k| in frequency domain
        for (int i = 0; i < m; i++) {
            int kyIndex = (i <= m / 2) ? i : i - m; // FFT ordering
            double ky = kyIndex * dky;
            for (int j = 0; j < n; j++) {
                int kxIndex = (j <= n / 2) ? j : j - n;
                double kx = kxIndex * dkx;
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