package com.ugcs.geohammer.math;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.index.kdtree.KdTree;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


class GridInterpolationTest {

    @Test
    void testIDWInterpolation() {
        // Create test data points in a grid pattern with a missing point in the middle
        KdTree kdTree = new KdTree();
        List<DataPoint> testValues = new ArrayList<>() {{
            add(new DataPoint(1.0, 1.0, 10.0));
            add(new DataPoint(1.0, 3.0, 20.0));
            add(new DataPoint(3.0, 1.0, 30.0));
            add(new DataPoint(3.0, 3.0, 40.0));
        }};

        for (DataPoint point : testValues) {
            kdTree.insert(new Coordinate(point.latitude(), point.longitude()), point);
        }

        // Test IDW interpolation with different parameters
        IDWInterpolator interpolator = new IDWInterpolator(
            kdTree,
            2.0,  // power
            4,    // minPoints
            3.0,  // maxSearchRadius
            1.0   // initialSearchRadius
        );

        // Interpolate at the center point (2.0, 2.0)
        double interpolatedValue = interpolator.interpolate(2.0, 2.0);

        // The interpolated value should be between the min and max of surrounding points
        assertTrue(interpolatedValue >= 10.0 && interpolatedValue <= 40.0,
            "Interpolated value should be between min and max of surrounding points");

        // Test with larger power parameter (more weight to closer points)
        IDWInterpolator interpolatorHighPower = new IDWInterpolator(
            kdTree,
            4.0,  // higher power
            4,    // minPoints
            3.0,  // maxSearchRadius
            1.0   // initialSearchRadius
        );

        double interpolatedValueHighPower = interpolatorHighPower.interpolate(2.0, 2.0);
        assertTrue(interpolatedValueHighPower >= 10.0 && interpolatedValueHighPower <= 40.0,
            "Interpolated value with high power should be between min and max");

        // The high power interpolation should give more weight to nearby points
        // so it should be different from the standard power interpolation
        //assertNotEquals(interpolatedValue, interpolatedValueHighPower,
        //    "Different power parameters should produce different interpolation results");
    }

    @Test
    void testInterpolationWithLargeCellSize() {
        // Create scattered test points
        KdTree kdTree = new KdTree();

        List<DataPoint> testValues = new ArrayList<>() {{
            add(new DataPoint(1.0, 1.0, 10.0));
            add(new DataPoint(1.5, 1.5, 15.0));
            add(new DataPoint(2.0, 2.0, 20.0));
            add(new DataPoint(4.0, 4.0, 40.0));
            add(new DataPoint(4.5, 4.5, 45.0));
            add(new DataPoint(5.0, 5.0, 50.0));
        }};

        for (DataPoint point : testValues) {
            kdTree.insert(new Coordinate(point.latitude(), point.longitude()), point);
        }

        // Test interpolation with large cell size
        IDWInterpolator interpolator = new IDWInterpolator(
            kdTree,
            2.0,  // power
            3,    // minPoints
            5.0,  // maxSearchRadius (large)
            2.0   // initialSearchRadius (large)
        );

        // Test points between clusters
        double interpolatedValue = interpolator.interpolate(3.0, 3.0);

        // Value should be smooth transition between clusters
        assertTrue(interpolatedValue >= 20.0 && interpolatedValue <= 40.0,
            "Interpolated value should provide smooth transition between clusters");

        // Test edge behavior
        double edgeValue = interpolator.interpolate(0.0, 0.0);
        assertFalse(Double.isNaN(edgeValue),
            "Edge interpolation should handle sparse data gracefully");
    }

    @Test
    void testCompareInterpolationMethods() {
        // Create test data with large gaps
        KdTree kdTree = new KdTree();

        List<DataPoint> testValues = new ArrayList<>() {{
            add(new DataPoint(1.0, 1.0, 10.0));
            add(new DataPoint(1.0, 9.0, 20.0));
            add(new DataPoint(9.0, 1.0, 30.0));
            add(new DataPoint(9.0, 9.0, 40.0));
        }};

        for (DataPoint point : testValues) {
            kdTree.insert(new Coordinate(point.latitude(), point.longitude()), point);
        }

        // Initialize IDW interpolator with parameters suitable for large cell sizes
        IDWInterpolator idwInterpolator = new IDWInterpolator(
            kdTree,
            2.0,  // power
            4,    // minPoints
            10.0, // maxSearchRadius (large enough to cover gaps)
            5.0   // initialSearchRadius
        );
        
        // Test points in the large gap
        double[][] testPoints = {
            {5.0, 5.0},  // center
            {3.0, 3.0},  // closer to low values
            {7.0, 7.0}   // closer to high values
        };

        for (double[] point : testPoints) {
            double idwValue = idwInterpolator.interpolate(point[0], point[1]);

            // IDW interpolation should produce values within the range of input data
            assertTrue(idwValue >= 10.0 && idwValue <= 40.0,
                "IDW interpolation should stay within input data range");

            // For points closer to certain values, interpolation should be weighted accordingly
            if (point[0] < 5.0 && point[1] < 5.0) {
                // Points in the lower-left quadrant should be closer to lower values
                assertTrue(idwValue < 25.0,
                    "Points closer to lower values should have lower interpolated values");
            } else if (point[0] > 5.0 && point[1] > 5.0) {
                // Points in the upper-right quadrant should be closer to higher values
                assertTrue(idwValue > 25.0,
                    "Points closer to higher values should have higher interpolated values");
            }
        }

        // Test edge behavior
        double edgeValue = idwInterpolator.interpolate(0.0, 0.0);
        assertTrue(edgeValue >= 10.0 && edgeValue <= 40.0,
            "Edge interpolation should produce reasonable values within input range");
    }
}
