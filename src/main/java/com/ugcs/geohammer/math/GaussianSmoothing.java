package com.ugcs.geohammer.math;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GaussianSmoothing {

    private static final Logger log = LoggerFactory.getLogger(GaussianSmoothing.class);

    // smaller kernels have no visible effect
    private static final double MIN_SIGMA = 0.5;

    // standard deviation of the kernel in cells
    private final double sigma;

    public GaussianSmoothing(double sigma) {
        this.sigma = sigma;
    }

    // NaN cells are skipped and stay NaN
    public float[][] apply(float[][] grid) {
        if (grid == null || grid.length == 0 || grid[0].length == 0 || sigma < MIN_SIGMA) {
            return grid;
        }

        float[] kernel = createKernel(sigma);
        int radius = kernel.length / 2;
        log.info("Applying low-pass filter with sigma {} cells", sigma);
        long start = System.nanoTime();

        int width = grid.length;
        int height = grid[0].length;

        // normalized convolution: values and weights of the non-NaN cells are convolved separately,
        // the kernel is separable, so both are convolved along y and then along x
        float[][] sums = new float[width][height];
        float[][] weights = new float[width][height];
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                float sum = 0;
                float weightSum = 0;
                for (int k = -radius; k <= radius; k++) {
                    int nj = j + k;
                    if (nj < 0 || nj >= height || Float.isNaN(grid[i][nj])) {
                        continue;
                    }
                    float weight = kernel[k + radius];
                    sum += weight * grid[i][nj];
                    weightSum += weight;
                }
                sums[i][j] = sum;
                weights[i][j] = weightSum;
            }
        }

        float[][] filtered = new float[width][height];
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                if (Float.isNaN(grid[i][j])) {
                    filtered[i][j] = Float.NaN;
                    continue;
                }
                float sum = 0;
                float weightSum = 0;
                for (int k = -radius; k <= radius; k++) {
                    int ni = i + k;
                    if (ni < 0 || ni >= width) {
                        continue;
                    }
                    float weight = kernel[k + radius];
                    sum += weight * sums[ni][j];
                    weightSum += weight * weights[ni][j];
                }
                // positive, as the cell itself is not NaN
                filtered[i][j] = sum / weightSum;
            }
        }

        log.info("Low-pass filter applied in {} ms", (int) ((System.nanoTime() - start) * 1e-6));
        return filtered;
    }

    private static float[] createKernel(double sigma) {
        int radius = (int) Math.ceil(3 * sigma);
        float[] kernel = new float[2 * radius + 1];
        for (int k = -radius; k <= radius; k++) {
            kernel[k + radius] = (float) Math.exp(-(k * k) / (2 * sigma * sigma));
        }
        return kernel;
    }
}
