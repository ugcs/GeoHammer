package com.ugcs.geohammer.math;

import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.util.Check;
import edu.emory.mathcs.jtransforms.fft.FloatFFT_2D;

import java.util.Arrays;

public class ReductionToPoleFilter {

    private static final double MIN_INCLINATION_DEGREES = 15.0;

    private final float[][] source;

    private final float[][] grid;

    private final int width;

    private final int height;

    private final double cellWidth;

    private final double cellHeight;

    private final double inclination;

    private final double declination;

    public ReductionToPoleFilter(float[][] grid, LatLon min, LatLon max, double inclinationDegrees,
                                 double declinationDegrees) {
        Check.notNull(grid);
        Check.condition(Math.abs(inclinationDegrees) >= MIN_INCLINATION_DEGREES,
                "Reduction to pole is unstable below 15 degrees magnetic inclination");
        Check.condition(Double.isFinite(declinationDegrees), "Magnetic declination must be finite");

        source = grid;
        width = grid.length;
        height = width > 0 ? grid[0].length : 0;
        this.grid = copy(grid);
        cellWidth = AnalyticSignalFilter.getCellWidth(grid, min, max);
        cellHeight = AnalyticSignalFilter.getCellHeight(grid, min, max);
        inclination = Math.toRadians(inclinationDegrees);
        declination = Math.toRadians(declinationDegrees);
        GridInterpolator.interpolate(this.grid, cellWidth, cellHeight);
    }

    public float[][] apply() {
        FloatFFT_2D fft = new FloatFFT_2D(width, height);
        float[][] values = new float[width][2 * height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                values[x][2 * y] = grid[x][y];
            }
        }
        fft.complexForward(values);

        double dkx = 2.0 * Math.PI / (width * cellWidth);
        double dky = 2.0 * Math.PI / (height * cellHeight);
        for (int x = 0; x < width; x++) {
            int xIndex = x <= width / 2 ? x : x - width;
            double kx = xIndex * dkx;
            for (int y = 0; y < height; y++) {
                int yIndex = y <= height / 2 ? y : y - height;
                double ky = yIndex * dky;
                double waveNumber = Math.hypot(kx, ky);
                if (waveNumber == 0) {
                    continue;
                }
                double horizontalProjection = Math.cos(inclination)
                        * (Math.sin(declination) * kx + Math.cos(declination) * ky) / waveNumber;
                double fieldReal = Math.sin(inclination);
                double denominatorReal = fieldReal * fieldReal - horizontalProjection * horizontalProjection;
                double denominatorImaginary = 2 * fieldReal * horizontalProjection;
                double denominatorMagnitude = denominatorReal * denominatorReal
                        + denominatorImaginary * denominatorImaginary;
                double multiplierReal = denominatorReal / denominatorMagnitude;
                double multiplierImaginary = -denominatorImaginary / denominatorMagnitude;
                float real = values[x][2 * y];
                float imaginary = values[x][2 * y + 1];
                values[x][2 * y] = (float) (real * multiplierReal - imaginary * multiplierImaginary);
                values[x][2 * y + 1] = (float) (real * multiplierImaginary + imaginary * multiplierReal);
            }
        }
        fft.complexInverse(values, true);

        float[][] transformed = new float[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                transformed[x][y] = Float.isNaN(source[x][y]) ? Float.NaN : values[x][2 * y];
            }
        }
        return transformed;
    }

    private static float[][] copy(float[][] values) {
        float[][] copy = new float[values.length][];
        for (int index = 0; index < values.length; index++) {
            copy[index] = Arrays.copyOf(values[index], values[index].length);
        }
        return copy;
    }
}
