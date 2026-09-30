package com.ugcs.geohammer.map.layer;

import java.awt.Color;

public final class HillShading {

	// light source direction in radians, counterclockwise from West
	private static final double AZIMUTH = Math.toRadians(315.0);

	// light source height in radians (0 - PI/2, 0 = horizon, PI/2 = zenith)
	private static final double ALTITUDE = Math.toRadians(45.0);

	// intensity of the hill-shading effect (0.0 - 1.0)
	private static final double INTENSITY = 0.5;

	public static final double MEAN_SLOPE = Math.toRadians(60.0);

	private HillShading() {
	}

	public static double getZFactor(float[][] grid, double meanSlope) {
		int width = grid.length;
		int height = width > 0 ? grid[0].length : 0;
		// geometric mean, unlike arithmetic, is not dominated by steep anomalies
		double logSum = 0;
		int n = 0;
		for (int x = 1; x < width - 1; x++) {
			for (int y = 1; y < height - 1; y++) {
				double dzdx = (grid[x + 1][y] - grid[x - 1][y]) / 2.0;
				double dzdy = (grid[x][y + 1] - grid[x][y - 1]) / 2.0;
				double gradient = Math.sqrt(dzdx * dzdx + dzdy * dzdy);
				// skips flat cells and NaN neighborhoods
				if (gradient > 0) {
					logSum += Math.log(gradient);
					n++;
				}
			}
		}
		if (n == 0) {
			return 1.0;
		}
		double meanGradient = Math.exp(logSum / n);
		return Math.tan(meanSlope) / meanGradient;
	}

	/**
	 * Calculates hill-shading illumination value for a given point in the grid.
	 *
	 * @param grid The grid data
	 * @param x        X coordinate in the grid
	 * @param y        Y coordinate in the grid
	 * @param zFactor  Vertical scale applied to the grid values before the slope calculation
	 * @return Illumination value between 0.0 (dark) and 1.0 (bright)
	 */
	public static double computeIllumination(float[][] grid, int x, int y, double zFactor) {
		// Calculate slope components, one-sided on the grid edges and next to blanked cells
		double dzdx = zFactor * derivative(grid, x, y, 1, 0);
		double dzdy = zFactor * derivative(grid, x, y, 0, 1);

		// Calculate slope and aspect
		double slope = Math.atan(Math.sqrt(dzdx * dzdx + dzdy * dzdy));
		double aspect = Math.atan2(dzdy, dzdx);

		// Calculate illumination using the hillshade formula
		double illumination = Math.cos(slope) * Math.sin(ALTITUDE) +
				Math.sin(slope) * Math.cos(ALTITUDE) *
						Math.cos(AZIMUTH - aspect);

		// Normalize illumination to [0, 1] range
		illumination = Math.max(0.0, illumination);

		return illumination;
	}

	// central difference in the (dx, dy) direction; one-sided when a neighbor
	// is outside the grid or blanked, zero when both are
	private static double derivative(float[][] grid, int x, int y, int dx, int dy) {
		float prev = getValue(grid, x - dx, y - dy);
		float next = getValue(grid, x + dx, y + dy);
		boolean hasPrev = !Float.isNaN(prev);
		boolean hasNext = !Float.isNaN(next);
		if (hasPrev && hasNext) {
			return (next - prev) / 2.0;
		}
		if (hasNext) {
			return next - grid[x][y];
		}
		if (hasPrev) {
			return grid[x][y] - prev;
		}
		return 0.0;
	}

	private static float getValue(float[][] grid, int x, int y) {
		return x >= 0 && x < grid.length && y >= 0 && y < grid[0].length
				? grid[x][y]
				: Float.NaN;
	}

	/**
	 * Applies hill-shading effect to a color.
	 *
	 * @param baseColor    The original color
	 * @param illumination Illumination value between 0.0 (dark) and 1.0 (bright)
	 * @return The shaded color
	 */
	public static Color apply(Color baseColor, double illumination) {
		// Blend between original color and shaded color based on intensity
		float shadeFactor = (float) (1.0 - (1.0 - illumination) * INTENSITY);

		// Convert int RGB values (0-255) to float (0.0-1.0), apply shading, and clamp to valid range
		float r = Math.max(0.0f, Math.min(1.0f, (baseColor.getRed() / 255.0f) * shadeFactor));
		float g = Math.max(0.0f, Math.min(1.0f, (baseColor.getGreen() / 255.0f) * shadeFactor));
		float b = Math.max(0.0f, Math.min(1.0f, (baseColor.getBlue() / 255.0f) * shadeFactor));

		// Keep the original alpha value
		return new Color(r, g, b, baseColor.getAlpha() / 255.0f);
	}
}
