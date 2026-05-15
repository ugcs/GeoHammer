package com.ugcs.geohammer.chart.tool.projection.model;

import com.ugcs.geohammer.chart.tool.projection.math.Polyline;
import com.ugcs.geohammer.chart.tool.projection.math.SnellRefraction;
import javafx.geometry.Point2D;

public record TraceRay(
        // mercator (x, y)
        Point2D world,
        // trajectory local (offset, altitude)
        Point2D origin,
        // unit direction in air
        Point2D direction,
        // where ray hits terrain (nullable)
        Point2D soilOrigin,
        // unit direction after Snell refraction in soil
        Point2D soilDirection,
        // distance from origin to soil origin
        double airGap
) {

    public static final double C_M_NS = 0.299792458; // m/ns

    public TraceRay(Point2D world, Point2D origin, Point2D direction, Point2D soilOrigin, Point2D soilDirection) {
        this(world, origin, direction, soilOrigin, soilDirection,
                soilOrigin != null ? origin.distance(soilOrigin) : Double.NaN);
    }

    public static TraceRay create(Point2D world, Point2D origin, Point2D direction,
                Polyline terrain, double erSqrt, boolean refract) {
        // terrain intersection
        Polyline.SegmentPoint soilHit = terrain.intersectRay(origin, direction);
        // refraction
        Point2D refracted = null;
        if (soilHit != null && refract) {
            Point2D soilNormal = terrain.getNormal(soilHit.segment());
            refracted = SnellRefraction.refract(direction, soilNormal, erSqrt);
        }
        return new TraceRay(
                world,
                origin,
                direction,
                soilHit != null ? soilHit.point() : null,
                soilHit != null ? (refracted != null ? refracted : direction) : null);
    }

    public Point2D positionAt(double t, double erSqrt) {
        if (soilOrigin == null) {
            // no terrain intersection, straight ray
            double dAir = C_M_NS * t;
            return origin.add(direction.multiply(dAir));
        }
        double airGap = airGap();
        double tAir = airGap / C_M_NS;
        if (t <= tAir) {
            // sample is in air
            double dAir = C_M_NS * t;
            return origin.add(direction.multiply(dAir));
        }
        double tSoil = t - tAir;
        double dSoil = C_M_NS / erSqrt * tSoil;
        return soilOrigin.add(soilDirection.multiply(dSoil));
    }
}
