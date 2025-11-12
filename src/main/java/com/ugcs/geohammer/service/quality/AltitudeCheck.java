package com.ugcs.geohammer.service.quality;

import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.math.SphericalMercator;
import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.model.LineSchema;
import com.ugcs.geohammer.model.Semantic;
import com.ugcs.geohammer.math.DouglasPeucker;
import com.ugcs.geohammer.util.IndexRange;
import javafx.geometry.Point2D;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.Polygon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AltitudeCheck extends FileQualityCheck {

    private static final double MIN_WIDTH = 0.15;
    private static final double DISTANCE_THRESHOLD = MIN_WIDTH / 2;

    private final GeometryFactory gf = new GeometryFactory();

    private final double max;
    private final double tolerance;
    private final double width;

    public AltitudeCheck(double max, double altitudeTolerance, double width) {
        this.max = max;
        this.tolerance = altitudeTolerance;
        this.width = Math.max(width, MIN_WIDTH);
    }

    @Override
    public List<QualityIssue> checkFile(CsvFile file) {
        return file != null ? checkValues(file.getGeoData()) : List.of();
    }

    private List<QualityIssue> checkValues(List<GeoData> values) {
        if (values == null) {
            return List.of();
        }

        // compute line components
        LineSchema lineSchema = new LineSchema(values);

        List<QualityIssue> issues = new ArrayList<>();
        for (Map.Entry<Integer, IndexRange> e : lineSchema.getRanges().entrySet()) {
            Integer lineIndex = e.getKey();
            IndexRange lineRange = e.getValue();
            LineComponents lineComponents = lineSchema.getComponents().get(lineIndex);

            List<LatLon> issuePoints = new ArrayList<>();
            for (int i = lineRange.from(); i < lineRange.to(); i++) {
                GeoData value = values.get(i);
                Number sensorValue = value.getNumberBySemantic(Semantic.ALTITUDE_AGL.getName());
                Double altitudeAgl = sensorValue != null
                        ? sensorValue.doubleValue()
                        : null;
                if (altitudeAgl == null || altitudeAgl.isNaN()) {
                    continue;
                }

                LatLon latlon = new LatLon(value.getLatitude(), value.getLongitude());
                if (!issuePoints.isEmpty() && latlon.getDistance(issuePoints.getLast()) < DISTANCE_THRESHOLD) {
                    // skip check by distance
                    continue;
                }

                if (altitudeAgl > max + tolerance) {
                    issuePoints.add(latlon);
                } else {
                    if (!issuePoints.isEmpty()) {
                        issues.addAll(createStripeIssues(issuePoints, lineComponents.getDirection()));
                        issuePoints = new ArrayList<>();
                    }
                }
            }
            // close range
            if (!issuePoints.isEmpty()) {
                issues.addAll(createStripeIssues(issuePoints, lineComponents.getDirection()));
            }
        }
        return issues;
    }

    private List<QualityIssue> createStripeIssues(List<LatLon> points, Point2D defaultDirection) {
        if (points.size() > 2) {
            List<Point2D> projected = points.stream().map(SphericalMercator::project).toList();
            List<Integer> indices = DouglasPeucker.approximatePolyline(
                    projected, 0.5 * width, 2);
            List<LatLon> selected = new ArrayList<>(indices.size());
            for (int i : indices) {
                selected.add(points.get(i));
            }
            points = selected;
        }
        if (points.size() == 1) {
            points.add(points.getFirst());
        }
        List<QualityIssue> issues = new ArrayList<>();
        for (int i = 0; i < points.size() - 1; i++) {
            issues.add(createStripeIssue(
                    points.get(i),
                    points.get(i + 1),
                    defaultDirection));
        }
        return issues;
    }

    private QualityIssue createStripeIssue(LatLon from, LatLon to, Point2D defaultDirection) {
        // Lproj / L = k
        double k = SphericalMercator.scaleFactorAt(from.getLatDgr());
        LineSegment segment = new LineSegment(
                Spatial.toCoordinate(SphericalMercator.project(from)),
                Spatial.toCoordinate(SphericalMercator.project(to)));

        double minLength = k * width;
        if (segment.getLength() < minLength) {
            segment = Spatial.expandSegment(segment, minLength,
                    Spatial.toCoordinate(defaultDirection));
        }
        // stripe in a projected space
        Polygon stripe = Spatial.orthoBuffer(segment, k * 0.5 * width);
        return new PolygonQualityIssue(
                QualityColors.ALTITUDE,
                gf.createPolygon(Spatial.toGeodetic(stripe.getCoordinates())));
    }
}
