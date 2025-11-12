package com.ugcs.geohammer.math;

import com.ugcs.geohammer.model.LatLon;
import javafx.geometry.Point2D;

// EPSG:3857
public final class SphericalMercator {

    // sphere radius
    public static final double R = 6378137.0;
    // R^-1
    public static final double R_INV = 1.0 / R;
    // max latitude in radians
    public static final double MAX_LATITUDE = 2.0 * Math.atan(Math.exp(Math.PI)) - Math.PI / 2.0;
    // projection extents size (single-axis)
    public static final double EXTENTS_SIZE = 2.0 * Math.PI * R;

    private SphericalMercator() {
    }

    public static Point2D project(LatLon geodetic) {
        if (geodetic == null) {
            return null;
        }
        return project(geodetic.getLatDgr(), geodetic.getLonDgr());
    }

    public static Point2D project(double latitude, double longitude) {
        // R - sphere radius
        // L = 2 * PI * R

        // lon: [-PI, PI) -> x [-L/2, L/2)
        // x = L/2 * (lon / PI)
        // x = R * lon
        double x = R * Math.toRadians(longitude);

        // lat: (-PI/2, PI/2)
        // y = L/2 * 1/PI * ln(tan(PI/4 + lat/2))
        // y = PI * R / PI * ln(tan(PI/4 + lat/2))
        // y = R * ln(tan(PI/4 + lat/2))
        double y = R * Math.log(Math.tan(Math.PI / 4.0 + Math.toRadians(latitude) / 2.0));
        return new Point2D(x, y);
    }

    public static LatLon restore(Point2D projected) {
        if (projected == null) {
            return null;
        }
        return restore(projected.getX(), projected.getY());
    }

    public static LatLon restore(double x, double y) {
        // x = R * lon
        // lon = R^-1 * x
        double longitude = Math.toDegrees(x / R);

        // y = R * ln(tan(PI/4 + lat/2))
        // ln(tan(PI/4 + lat/2)) = y/R
        // tan(PI/4 + lat/2) = e^(y/R)
        // lat/2 + PI/4 = atan(e^(y/R))
        // lat = 2 * atan(e^(y/R)) - PI/2
        double latitude = Math.toDegrees(2.0 * Math.atan(Math.exp(R_INV * y)) - Math.PI / 2.0);
        return new LatLon(latitude, longitude);
    }

    public static double scaleFactorAt(double latitude) {
        // Lproj / L = k = sec(lat)
        return 1.0 / Math.cos(Math.toRadians(latitude));
    }

    public static double areaScaleFactorAt(double latitude) {
        double k = scaleFactorAt(latitude);
        return k * k;
    }
}