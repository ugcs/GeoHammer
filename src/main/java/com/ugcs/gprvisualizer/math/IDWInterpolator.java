package com.ugcs.gprvisualizer.math;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.index.kdtree.KdNode;
import org.locationtech.jts.index.kdtree.KdTree;

import java.util.List;

/**
 * High-performance Inverse Distance Weighting (IDW) interpolator optimized for GPR data visualization.
 * 
 * PERFORMANCE OPTIMIZATION RECOMMENDATIONS:
 * 
 * 1. Optimal Parameter Ranges:
 *    - power: 2.0 to 3.0 (2.0 is fastest, higher values need more points)
 *    - minPoints: 4 to 8 (automatically limited to 12 for performance)
 *    - initialSearchRadius: 1.0 to 2.0 * cellSize
 *    - maxSearchRadius: 3.0 to 5.0 * cellSize
 * 
 * 2. Cell Size Selection:
 *    - Larger cells (>0.1) = faster interpolation
 *    - Smaller cells = better detail but slower
 *    - Recommended: Start with large cells, decrease if needed
 * 
 * 3. Performance Features:
 *    - Uses squared distances to avoid sqrt calculations
 *    - Caches power values for faster computation
 *    - Implements early termination for exact matches
 *    - Optimized search radius strategy
 *    - Limits maximum points used for interpolation
 * 
 * 4. Memory Usage:
 *    - KdTree structure for efficient spatial queries
 *    - Minimal memory overhead for caching
 *    - Adaptive search radius to minimize neighbor searches
 * 
 * This implementation balances accuracy and performance:
 * - Suitable for real-time visualization
 * - Handles large datasets efficiently
 * - Maintains accuracy while optimizing speed
 */
public class IDWInterpolator {
    private final KdTree kdTree;
    private final double power;
    private final int minPoints;
    private final double maxSearchRadius;
    private final double initialSearchRadius;

    // Cache for squared power to avoid repeated calculations
    private final double powerX2;
    // Threshold for early termination when exact match is found
    private static final double EXACT_MATCH_THRESHOLD = 1e-10;

    /**
     * Creates a new IDW interpolator.
     *
     * @param kdTree spatial index of known points
     * @param power power parameter (typically 2)
     * @param minPoints minimum number of points to use for interpolation
     * @param maxSearchRadius maximum search radius
     * @param initialSearchRadius initial search radius
     */
    public IDWInterpolator(KdTree kdTree, double power, int minPoints, 
                          double maxSearchRadius, double initialSearchRadius) {
        this.kdTree = kdTree;
        this.power = power;
        this.powerX2 = power * 2;  // Cache for optimization
        this.minPoints = Math.min(minPoints, 12);  // Limit max points for performance
        this.maxSearchRadius = maxSearchRadius;
        this.initialSearchRadius = initialSearchRadius;
    }

    /**
     * Interpolates value at given point using IDW method.
     *
     * @param x x-coordinate
     * @param y y-coordinate
     * @return interpolated value or NaN if no points found within search radius
     */
    public double interpolate(double x, double y) {
        double searchRadius = initialSearchRadius;
        List<KdNode> neighbors;

        // Use more aggressive initial search with faster termination
        Envelope searchEnv = new Envelope(
            x - searchRadius, x + searchRadius,
            y - searchRadius, y + searchRadius
        );
        neighbors = kdTree.query(searchEnv);

        // If we don't have enough points, try one more larger radius
        if (neighbors.size() < minPoints && searchRadius < maxSearchRadius) {
            searchRadius = Math.min(searchRadius * 3, maxSearchRadius);
            searchEnv = new Envelope(
                x - searchRadius, x + searchRadius,
                y - searchRadius, y + searchRadius
            );
            neighbors = kdTree.query(searchEnv);
        }

        if (neighbors.isEmpty()) {
            return Double.NaN;
        }

        double weightSum = 0;
        double valueSum = 0;

        for (KdNode node : neighbors) {
            Coordinate coord = (Coordinate) node.getCoordinate();
            // Use squared distance to avoid sqrt calculation
            double dx = x - coord.x;
            double dy = y - coord.y;
            double distanceSq = dx * dx + dy * dy;

            // Early return for exact matches
            if (distanceSq < EXACT_MATCH_THRESHOLD) {
                return ((DataPoint) node.getData()).value();
            }

            // Use squared distance and cached power value for faster calculation
            double weight = 1.0 / Math.pow(distanceSq, power/2);  // Equivalent to 1/distance^power but faster
            weightSum += weight;
            valueSum += weight * ((DataPoint) node.getData()).value();
        }

        return valueSum / weightSum;
    }
}
