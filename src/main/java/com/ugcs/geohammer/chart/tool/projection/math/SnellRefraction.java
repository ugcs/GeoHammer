package com.ugcs.geohammer.chart.tool.projection.math;

import javafx.geometry.Point2D;

public final class SnellRefraction {

    private SnellRefraction() {
    }

    // Snell's law: returns refracted unit direction, or null for total internal reflection
    public static Point2D refract(Point2D incident, Point2D normal, double erSqrt) {
        double eta = 1 / erSqrt; // air -> soil
        double cosI = -incident.dotProduct(normal);

        // ensure normal points against incident ray
        if (cosI < 0) {
            normal = normal.multiply(-1);
            cosI = -cosI;
        }

        double sinI2 = 1 - cosI * cosI;
        double k = 1 - eta * eta * sinI2;
        if (k < 0) {
            return null; // total internal reflection
        }

        double cosT = Math.sqrt(k);
        return incident.multiply(eta)
                .add(normal.multiply(eta * cosI - cosT))
                .normalize();
    }
}
