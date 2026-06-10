package com.ugcs.geohammer.geotagger.domain;

import com.ugcs.geohammer.math.interpolation.LinearInterpolator;
import com.ugcs.geohammer.math.interpolation.SplineInterpolator;
import com.ugcs.geohammer.util.Check;

public record SplineStencil(
    Position p2,
    Position p1,
    Position n1,
    Position n2
) {

    public SplineStencil {
        Check.notNull(p1);
        Check.notNull(n1);
        if (p2 == null) {
            p2 = p1;
        }
        if (n2 == null) {
            n2 = n1;
        }
    }

    public Position interpolate(long time) {
        double latitude = SplineInterpolator.interpolate(time,
                p2.time(), p2.latitude(), p1.time(), p1.latitude(),
                n1.time(), n1.latitude(), n2.time(), n2.latitude());
        double longitude = SplineInterpolator.interpolate(time,
                p2.time(), p2.longitude(), p1.time(), p1.longitude(),
                n1.time(), n1.longitude(), n2.time(), n2.longitude());
        // altitude: linear over the bracketing pair
        double altitude = LinearInterpolator.interpolate(time,
                p1.time(), p1.altitude(),
                n1.time(), n1.altitude());
        return new Position(time, latitude, longitude, altitude);
    }
}
