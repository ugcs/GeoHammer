package com.ugcs.geohammer.service.quality;

import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.math.SphericalMercator;
import com.ugcs.geohammer.util.Check;
import javafx.geometry.Point2D;

public class LineComponents {

    // point in a spherical meractor projection
    private final Point2D point;

    // direction vector in a projected space, normalized
    private final Point2D direction;

    public LineComponents(Point2D point, Point2D direction) {
        Check.notNull(point);
        Check.notNull(direction);

        this.point = point;
        this.direction = direction.normalize();
    }

    public Point2D getPoint() {
        return point;
    }

    public Point2D getDirection() {
        return direction;
    }

    public double getDistanceToPoint(LatLon p) {
        if (p == null) {
            return 0;
        }

        // vector from the line point to target
        Point2D ab = SphericalMercator.project(p).subtract(point);
        // compute projected distance by cross product,
        // do not scale by direction vector size, as is it is always normalized
        double d = Math.abs(ab.getX() * direction.getY()
                - ab.getY() * direction.getX());
        double f = SphericalMercator.scaleFactorAt(p.getLatDgr());
        return d / f;
    }

    public LatLon projectToLine(LatLon p) {
        // vector from the line point to target
        Point2D ab = SphericalMercator.project(p).subtract(point);
        // scalar projection to line
        double k = ab.dotProduct(direction);
        return SphericalMercator.restore(point.add(direction.multiply(k)));
    }
}
