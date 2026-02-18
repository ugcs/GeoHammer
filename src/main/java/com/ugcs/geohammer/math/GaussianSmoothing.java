package com.ugcs.geohammer.math;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GaussianSmoothing {

    private static final Logger log = LoggerFactory.getLogger(GaussianSmoothing.class);

    private final int kernelSize;

    private final int kernelRadius;

    public GaussianSmoothing() {
        this(15, 7);
    }

    public GaussianSmoothing(int kernelSize, int kernelRadius) {
        this.kernelSize = kernelSize;
        this.kernelRadius = kernelRadius;
    }

    /**
     * Applies a low-pass filter to the grid data to smooth out high-frequency variations.
     * Uses a Gaussian kernel for the convolution.
     *
     * @param grid The grid data to filter
     */
    public float[][] apply(float[][] grid) {
        if (grid == null || grid.length == 0 || grid[0].length == 0) {
            return grid;
        }

        log.info("Applying low-pass filter with {}x{} kernel",
                kernelSize,
                kernelSize);
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

        int width = grid.length;
        int height = grid[0].length;

        var filtered = copyGrid(grid);

        // Apply convolution
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                // Skip NaN values
                if (Float.isNaN(grid[i][j])) {
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
                        if (ni < 0 || ni >= width || nj < 0 || nj >= height || Float.isNaN(grid[ni][nj])) {
                            continue;
                        }

                        float weight = kernel[ki + kernelRadius][kj + kernelRadius];
                        sum2 += grid[ni][nj] * weight;
                        weightSum += weight;
                    }
                }

                // Normalize by the sum of weights
                if (weightSum > 0) {
                    filtered[i][j] = sum2 / weightSum;
                }
            }
        }

        log.info("Low-pass filter applied in {} ms",
                System.currentTimeMillis() - startTime);
        return filtered;
    }


    private static float[][] copyGrid(float[][] grid) {
        var copy = new float[grid.length][];
        for (int i = 0; i < grid.length; i++) {
            copy[i] = grid[i].clone();
        }
        return copy;
    }
}
