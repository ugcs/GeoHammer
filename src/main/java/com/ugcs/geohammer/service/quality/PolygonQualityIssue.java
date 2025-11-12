package com.ugcs.geohammer.service.quality;

import com.ugcs.geohammer.util.Check;
import org.locationtech.jts.geom.Polygon;

import java.awt.Color;

public class PolygonQualityIssue extends QualityIssue {

    private final Polygon polygon;

    public PolygonQualityIssue(Color color, Polygon polygon) {
        super(color);

        Check.notNull(polygon);

        this.polygon = polygon;
    }

    public Polygon getPolygon() {
        return polygon;
    }
}
