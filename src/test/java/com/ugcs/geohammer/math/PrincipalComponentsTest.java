package com.ugcs.geohammer.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import javafx.geometry.Point2D;
import org.junit.jupiter.api.Test;

class PrincipalComponentsTest {

    private static final double DELTA = 1e-9;

    @Test
    void findOffsetComponents_stepsAlongDirection_returnsDirectionAsPrimary() {
        double angle = Math.toRadians(30);
        double[] dx = {Math.cos(angle), 2 * Math.cos(angle), 0.5 * Math.cos(angle)};
        double[] dy = {Math.sin(angle), 2 * Math.sin(angle), 0.5 * Math.sin(angle)};

        PrincipalComponents.Components2 components = findStepComponents(dx, dy);

        assertEquals(30, axisAngle(components.primary()), 1e-6);
    }

    @Test
    void findOffsetComponents_oppositeSteps_returnsSameDirection() {
        double[] dx = {1, -1, 1, -1};
        double[] dy = {2, -2, 2, -2};
        double[] flippedDx = {1, 1, 1, 1};
        double[] flippedDy = {2, 2, 2, 2};

        PrincipalComponents.Components2 components = findStepComponents(dx, dy);
        PrincipalComponents.Components2 flipped = findStepComponents(flippedDx, flippedDy);

        assertEquals(axisAngle(flipped.primary()), axisAngle(components.primary()), 1e-6);
        assertEquals(Math.toDegrees(Math.atan2(2, 1)), axisAngle(components.primary()), 1e-6);
    }

    @Test
    void findOffsetComponents_longerSteps_dominateDirection() {
        PrincipalComponents.Components2 alongX = findStepComponents(new double[] {3, 0}, new double[] {0, 1});
        PrincipalComponents.Components2 alongY = findStepComponents(new double[] {1, 0}, new double[] {0, 3});

        assertEquals(0, axisAngle(alongX.primary()), 1e-6);
        assertEquals(90, axisAngle(alongY.primary()), 1e-6);
    }

    @Test
    void findOffsetComponents_longStepAndShortSteps_countsStepsByLength() {
        // 4 along x in one step and 5 along y in five steps:
        // squared lengths would make the long step dominate
        double[] dx = {4, 0, 0, 0, 0, 0};
        double[] dy = {0, 1, 1, 1, 1, 1};

        PrincipalComponents.Components2 components = findStepComponents(dx, dy);

        assertEquals(90, axisAngle(components.primary()), 1e-6);
    }

    @Test
    void findOffsetComponents_repeatedPoint_ignoresZeroStep() {
        PrincipalComponents.Components2 components = PrincipalComponents.findOffsetComponents(
                new double[] {0, 2, 2}, new double[] {0, 0, 0});

        assertEquals(0, axisAngle(components.primary()), 1e-6);
        assertEquals(1, components.primary().magnitude(), DELTA);
    }

    @Test
    void findOffsetComponents_orthogonalSteps_returnsLengthSharesAndRatio() {
        PrincipalComponents.Components2 components = findStepComponents(new double[] {4, 0}, new double[] {0, 1});

        // 4 of the total length of 5 along x, 1 along y
        assertEquals(0.8, components.primary().magnitude(), DELTA);
        assertEquals(0.2, components.secondary().magnitude(), DELTA);
        assertEquals(2, components.ratio(), DELTA);
        assertEquals(0, components.primary().dotProduct(components.secondary()), DELTA);
    }

    @Test
    void findOffsetComponents_noSteps_returnsUnitAxes() {
        PrincipalComponents.Components2 empty = PrincipalComponents.findOffsetComponents(
                new double[0], new double[0]);
        PrincipalComponents.Components2 single = PrincipalComponents.findOffsetComponents(
                new double[] {5}, new double[] {7});
        PrincipalComponents.Components2 repeated = PrincipalComponents.findOffsetComponents(
                new double[] {5, 5}, new double[] {7, 7});

        for (PrincipalComponents.Components2 components : new PrincipalComponents.Components2[] {
                empty, single, repeated}) {
            assertEquals(new Point2D(1, 0), components.primary());
            assertEquals(new Point2D(0, 1), components.secondary());
        }
    }

    @Test
    void findOffsetComponents_differentLengths_throws() {
        assertThrows(RuntimeException.class, () -> PrincipalComponents.findOffsetComponents(
                new double[] {1, 2}, new double[] {1}));
    }

    // components of the path that starts at the origin and makes the given steps
    private static PrincipalComponents.Components2 findStepComponents(double[] dx, double[] dy) {
        double[] x = new double[dx.length + 1];
        double[] y = new double[dy.length + 1];
        for (int i = 0; i < dx.length; i++) {
            x[i + 1] = x[i] + dx[i];
            y[i + 1] = y[i] + dy[i];
        }
        return PrincipalComponents.findOffsetComponents(x, y);
    }

    // angle of an undirected axis in degrees, [0, 180)
    private static double axisAngle(Point2D vector) {
        double angle = Math.toDegrees(Math.atan2(vector.getY(), vector.getX())) % 180;
        return angle < 0 ? angle + 180 : angle;
    }
}
