package com.ugcs.gprvisualizer.event;

/**
 * Event class for configuring grid interpolation parameters.
 * 
 * Supports two interpolation methods:
 * 1. SPLINES - Traditional spline interpolation, suitable for:
 *    - Dense, evenly spaced data
 *    - Small to medium cell sizes
 *    - Smooth continuous surfaces
 * 
 * 2. IDW (Inverse Distance Weighting) - Alternative method, better for:
 *    - Large cell sizes
 *    - Sparse or irregular data
 *    - Avoiding interpolation artifacts
 *    
 * IDW parameters:
 * - Power: Controls how quickly influence decreases with distance (typically 2.0)
 * - MinPoints: Minimum number of points to use for interpolation
 * - Search radius: Automatically adjusted based on cell size
 * 
 * Hill-shading parameters:
 * - Enabled: Whether hill-shading is applied to the grid
 * - Azimuth: Direction of the light source in degrees (0-360, 0=North, 90=East)
 * - Altitude: Height of the light source in degrees (0-90, 0=horizon, 90=zenith)
 * - Intensity: Strength of the hill-shading effect (0.0-1.0)
 */
public class GriddingParamsSetted extends BaseEvent {

    public enum InterpolationMethod {
        SPLINES,
        IDW
    }

    private final double cellSize;
    private final double blankingDistance;
    private final boolean toAll;
    private final InterpolationMethod interpolationMethod;
    private final double idwPower;
    private final int idwMinPoints;

    // Analytic signal parameters
    private final boolean analyticSignalEnabled;

    // Hill-shading parameters
    private final boolean hillShadingEnabled;
    private final boolean smoothingEnabled;

    private final double hillShadingAzimuth;
    private final double hillShadingAltitude;
    private final double hillShadingIntensity;

    // Default and limit values for IDW parameters
    public static final double DEFAULT_POWER = 2.0;
    public static final int DEFAULT_MIN_POINTS = 6;
    private static final double MIN_CELL_SIZE = 0.01;
    private static final double MAX_POWER = 3.0;
    private static final int MAX_MIN_POINTS = 12;
    public static final double IDW_CELL_SIZE_THRESHOLD = 0.09;

    // Limit values for hill-shading parameters
    public static final double MIN_HILL_SHADING_INTENSITY = 0.0;
    public static final double MAX_HILL_SHADING_INTENSITY = 1.0;

    public GriddingParamsSetted(Object source, double cellSize, double blankingDistance, boolean toAll,
                               InterpolationMethod interpolationMethod, double idwPower, int idwMinPoints,
                               boolean analyticSignalEnabled,
                               boolean hillShadingEnabled, boolean smoothingEnabled,
                                double hillShadingAzimuth, double hillShadingAltitude,
                               double hillShadingIntensity) {
        super(source);

        // Validate and adjust parameters for optimal performance
        this.cellSize = Math.max(cellSize, MIN_CELL_SIZE);
        this.blankingDistance = blankingDistance;
        this.toAll = toAll;
        this.interpolationMethod = interpolationMethod;

        // Optimize IDW parameters for better performance
        this.idwPower = interpolationMethod == InterpolationMethod.IDW ?
            Math.min(Math.max(idwPower, DEFAULT_POWER), MAX_POWER) : DEFAULT_POWER;
        this.idwMinPoints = interpolationMethod == InterpolationMethod.IDW ?
            Math.min(Math.max(idwMinPoints, DEFAULT_MIN_POINTS), MAX_MIN_POINTS) : DEFAULT_MIN_POINTS;

        // Set analytic signal parameters
        this.analyticSignalEnabled = analyticSignalEnabled;

        // Set hill-shading parameters
        this.hillShadingEnabled = hillShadingEnabled;
        this.smoothingEnabled = smoothingEnabled;
        this.hillShadingAzimuth = hillShadingAzimuth;
        this.hillShadingAltitude = hillShadingAltitude;
        this.hillShadingIntensity = Math.clamp(hillShadingIntensity, MIN_HILL_SHADING_INTENSITY, MAX_HILL_SHADING_INTENSITY);
    }

    public double getCellSize() {
        return cellSize;
    }

    public double getBlankingDistance() {
        return blankingDistance;
    }

    public boolean isToAll() {
        return toAll;
    }

    public InterpolationMethod getInterpolationMethod() {
        return interpolationMethod;
    }

    public double getIdwPower() {
        return idwPower;
    }

    public int getIdwMinPoints() {
        return idwMinPoints;
    }

    public boolean isAnalyticSignalEnabled() {
        return analyticSignalEnabled;
    }

    public boolean isHillShadingEnabled() {
        return hillShadingEnabled;
    }

    public boolean isSmoothingEnabled() {
        return smoothingEnabled;
    }

    public double getHillShadingAzimuth() {
        return hillShadingAzimuth;
    }

    public double getHillShadingAltitude() {
        return hillShadingAltitude;
    }

    public double getHillShadingIntensity() {
        return hillShadingIntensity;
    }
}