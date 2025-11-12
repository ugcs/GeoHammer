package com.ugcs.geohammer.service.quality;

import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.math.SphericalMercator;
import javafx.geometry.Point2D;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.buffer.BufferParameters;
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier;

public final class Spatial {

    private static final GeometryFactory gf = new GeometryFactory();

    public static final double EARTH_CIRCUMFERENCE = 40075017;

    public static final double DEGREES_IN_METER = 360.0 / EARTH_CIRCUMFERENCE;

    private Spatial() {
    }

    public static double toDegrees(double meters) {
        return meters * DEGREES_IN_METER;
    }

    public static Coordinate toCoordinate(Point2D point) {
        if (point == null) {
            return null;
        }
        return new Coordinate(point.getX(), point.getY());
    }

    public static Point2D toPoint2D(Coordinate coordinate) {
        if (coordinate == null) {
            return null;
        }
        return new Point2D(coordinate.getX(), coordinate.getY());
    }

    public static Coordinate toCoordinate(LatLon latlon) {
        if (latlon == null) {
            return null;
        }
        return new Coordinate(latlon.getLonDgr(), latlon.getLatDgr());
    }

    public static LatLon toLatLon(Coordinate coordinate) {
        if (coordinate == null) {
            return null;
        }
        return new LatLon(coordinate.getY(), coordinate.getX());
    }

    public static Coordinate[] toGeodetic(Coordinate[] projected) {
        if (projected == null) {
            return null;
        }
        int n = projected.length;
        Coordinate[] geodetic = new Coordinate[n];
        for (int i = 0; i < n; i++) {
            Coordinate point = projected[i];
            geodetic[i] = toCoordinate(SphericalMercator.restore(point.getX(), point.getY()));
        }
        return geodetic;
    }

    public static Coordinate[] toProjected(Coordinate[] geodetic) {
        if (geodetic == null) {
            return null;
        }
        int n = geodetic.length;
        Coordinate[] projected = new Coordinate[n];
        for (int i = 0; i < n; i++) {
            Coordinate point = geodetic[i];
            projected[i] = toCoordinate(SphericalMercator.project(point.getY(), point.getX()));
        }
        return projected;
    }

    // tolerance is in meters
    public static Polygon simplifyPolygon(Polygon polygon, double tolerance) {
        if (polygon == null) {
            return null;
        }
        Geometry simplified = DouglasPeuckerSimplifier.simplify(polygon, tolerance);
        if (simplified instanceof Polygon simplifiedPolygon) {
            return simplifiedPolygon;
        }
        return null;
    }

    public static LineString simplifyLine(LineString line, double tolerance) {
        if (line == null) {
            return null;
        }
        Geometry simplified = DouglasPeuckerSimplifier.simplify(line, tolerance);
        if (simplified instanceof LineString simplifiedLine) {
            return simplifiedLine;
        }
        return null;
    }

    public static Polygon createStripe(LineString line, double width) {
        if (line == null) {
            return null;
        }

        double offset = 0.5 * width;
        Geometry buffered = line.buffer(offset, BufferParameters.CAP_FLAT);
        if (buffered instanceof Polygon stripe) {
            return stripe;
        }
        return null;
    }

    public static Polygon orthoBuffer(LineSegment segment, double depth) {
        if (segment == null) {
            return null;
        }

        Coordinate a = segment.getCoordinate(0);
        Coordinate b = segment.getCoordinate(1);

        double dx = b.getX() - a.getX();
        double dy = b.getY() - a.getY();
        double l = Math.sqrt(dx * dx + dy * dy);
        double nX = (-dy / l) * depth;
        double nY = (dx / l) * depth;

        Coordinate p1 = new Coordinate(a.getX() - nX, a.getY() - nY);
        Coordinate p2 = new Coordinate(a.getX() + nX, a.getY() + nY);
        Coordinate p3 = new Coordinate(b.getX() + nX, b.getY() + nY);
        Coordinate p4 = new Coordinate(b.getX() - nX, b.getY() - nY);

        return gf.createPolygon(new Coordinate[] { p1, p2, p3, p4, p1 });
    }

    public static LineSegment expandSegment(LineSegment segment, double newLength, Coordinate defaultDirection) {
        if (segment == null) {
            return null;
        }
        Point2D a = toPoint2D(segment.p0);
        Point2D b = toPoint2D(segment.p1);
        Point2D ab = b.subtract(a);

        // result
        Point2D a2;
        Point2D b2;

        double l = ab.magnitude();
        if (l > 1e-6) {
            // growth factor of ab on both ends
            double k = 0.5 * (newLength + l) / l;

            a2 = b.add(ab.multiply(-k));
            b2 = a.add(ab.multiply(k));
        } else {
            if (defaultDirection == null) {
                defaultDirection = new Coordinate(0, 1);
            }
            // half of the target length
            double h = 0.5 * newLength;
            Point2D offset = toPoint2D(defaultDirection)
                    .normalize()
                    .multiply(h);

            a2 = a.add(offset);
            b2 = a.subtract(offset);
        }

        return new LineSegment(toCoordinate(a2), toCoordinate(b2));
    }
}
