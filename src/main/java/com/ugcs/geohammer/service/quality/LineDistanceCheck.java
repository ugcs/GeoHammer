package com.ugcs.geohammer.service.quality;

import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.math.SphericalMercator;
import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.IndexRange;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;

public class LineDistanceCheck implements QualityCheck {

    private static final double HULL_CONVEXITY = 4;

    private static final double MIN_DISTANCE = 0.15;

    private static final double SIMPLIFY_TOLERANCE = 0.03;

    private final GeometryFactory gf = new GeometryFactory();

    // projection scale factor for the current value set
    private double projectionScaleFactor;

    // max distance between lines in meters
    private final double distance;

    public LineDistanceCheck(double distance) {
        Check.condition(distance >= 0);

        this.distance = Math.max(distance, MIN_DISTANCE);
    }

    @Override
    public List<QualityIssue> check(List<CsvFile> files) {
        if (files == null) {
            return List.of();
        }

        double k = getProjectionScaleFactor(files);
        List<Coordinate> points = getAllPoints(files);
        Polygon contour = new ConcaveHull().concaveHull(points,
                HULL_CONVEXITY, k * distance);
        if (contour == null) {
            return List.of();
        }
        Polygon simplifiedContour = Spatial.simplifyPolygon(contour,
                k * SIMPLIFY_TOLERANCE);
        if (simplifiedContour != null) {
            contour = simplifiedContour;
        }

        List<Polygon> stripes = createStripes(files);
        List<Polygon> uncovered = cutStripes(contour, stripes);

        List<QualityIssue> issues = new ArrayList<>();
        for (Polygon polygon : uncovered) {
            issues.add(createPolygonIssue(polygon));
        }
        return issues;
    }

    private double getProjectionScaleFactor(List<CsvFile> files) {
        for (CsvFile file : files) {
            List<GeoData> values = file.getGeoData();
            if (values == null || values.isEmpty()) {
                continue;
            }
            GeoData first = values.getFirst();
            return SphericalMercator.scaleFactorAt(first.getLatitude());
        }
        return 1.0;
    }

    private List<Polygon> cutStripes(Polygon contour, List<Polygon> stripes) {
        List<Polygon> shapes = new ArrayList<>();
        shapes.add(contour);

        for (Polygon stripe : stripes) {
            List<Polygon> cutShapes = new ArrayList<>();
            for (Polygon shape : shapes) {
                Geometry cutShape = shape.difference(stripe);
                cutShapes.addAll(getPolygons(cutShape));
            }
            shapes = cutShapes;
        }
        return shapes;
    }

    private List<Polygon> getPolygons(Geometry geometry) {
        if (geometry == null) {
            return List.of();
        }
        List<Polygon> result = new ArrayList<>();
        if (geometry instanceof Polygon polygon) {
            result.add(polygon);
        }
        if (geometry instanceof MultiPolygon multiPolygon) {
            for (int i = 0; i < multiPolygon.getNumGeometries(); i++) {
                result.addAll(getPolygons(multiPolygon.getGeometryN(i)));
            }
        }
        return result;
    }

    private List<Coordinate> getAllPoints(List<CsvFile> files) {
        List<Coordinate> points = new ArrayList<>();
        for (CsvFile file : files) {
            List<GeoData> values = file.getGeoData();
            if (values == null || values.isEmpty()) {
                continue;
            }
            NavigableMap<Integer, IndexRange> ranges = file.getLineRanges();
            for (IndexRange range : ranges.values()) {
                addLinePoints(points, values, range);
            }
        }
        return points;
    }

    private void addLinePoints(List<Coordinate> points, List<GeoData> values, IndexRange range) {
        // skip points within half of the max line distance
        double k = SphericalMercator.scaleFactorAt(values.getFirst().getLatitude());
        double th = k * 0.5 * distance;

        Coordinate last = null;
        for (int i = range.from(); i < range.to(); i++) {
            GeoData value = values.get(i);
            Coordinate point = Spatial.toCoordinate(
                    SphericalMercator.project(value.getLatitude(), value.getLongitude()));
            if (last == null || i == range.to() - 1 || last.distance(point) > th) {
                points.add(point);
                last = point;
            }
        }
    }

    private List<Polygon> createStripes(List<CsvFile> files) {
        List<Polygon> stripes = new ArrayList<>();
        for (CsvFile file : files) {
            List<GeoData> values = file.getGeoData();
            if (values == null || values.isEmpty()) {
                continue;
            }
            double k = SphericalMercator.scaleFactorAt(values.getFirst().getLatitude());
            NavigableMap<Integer, IndexRange> ranges = file.getLineRanges();
            for (IndexRange range : ranges.values()) {
                LineString line = getLine(values, range);
                line = Spatial.simplifyLine(line, k * SIMPLIFY_TOLERANCE);
                Polygon stripe = Spatial.createStripe(line, k * distance);
                if (stripe != null) {
                    stripes.add(stripe);
                }
            }
        }
        return stripes;
    }

    private LineString getLine(List<GeoData> values, IndexRange range) {
        int n = range.size();
        if (n == 0) {
            return null;
        }

        Coordinate[] points = new Coordinate[n];
        for (int i = 0; i < n; i++) {
            GeoData value = values.get(range.from() + i);
            points[i] = Spatial.toCoordinate(
                    SphericalMercator.project(value.getLatitude(), value.getLongitude()));
        }
        // line string should contain 0 or 2+ points
        if (points.length == 1) {
            points = new Coordinate[] { points[0], points[0] };
        }
        return gf.createLineString(points);
    }

    private QualityIssue createPolygonIssue(Polygon polygon) {
        return new PolygonQualityIssue(
                QualityColors.LINE_DISTANCE,
                gf.createPolygon(Spatial.toGeodetic(polygon.getCoordinates())));
    }
}
