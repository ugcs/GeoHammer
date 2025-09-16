package com.ugcs.gprvisualizer.draw;

import com.github.thecoldwine.sigrun.common.ext.GoogleCoordUtils;
import com.github.thecoldwine.sigrun.common.ext.LatLon;
import com.github.thecoldwine.sigrun.common.ext.MapField;
import com.ugcs.gprvisualizer.app.quality.PointQualityIssue;
import com.ugcs.gprvisualizer.app.quality.PolygonQualityIssue;
import com.ugcs.gprvisualizer.app.quality.QualityIssue;
import com.ugcs.gprvisualizer.app.quality.Spatial;
import com.ugcs.gprvisualizer.event.FileSelectedEvent;
import com.ugcs.gprvisualizer.event.WhatChanged;
import com.ugcs.gprvisualizer.utils.Check;
import javafx.geometry.Point2D;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.geom.Path2D;
import java.util.List;

@Component
public class QualityLayer extends BaseLayer {

    private List<QualityIssue> issues;

    public void setIssues(List<QualityIssue> issues) {
        this.issues = issues;
    }

    @EventListener
    private void onFileSelected(FileSelectedEvent event) {
        if (event.getFile() == null && issues != null && !issues.isEmpty()) {
            issues.clear();
        }
    }

    @EventListener
    private void somethingChanged(WhatChanged changed) {
        if (changed.isTraceCut()) {
            if (issues != null) {
                issues.clear();
            }
        }
    }

    @Override
    public void draw(Graphics2D g2, MapField field) {
        Check.notNull(g2);
        Check.notNull(field);

        if (!isActive()) {
            return;
        }

        if (issues == null || issues.isEmpty()) {
            return;
        }

        for (QualityIssue issue : issues) {
            if (issue instanceof PointQualityIssue pointIssue) {
                drawPointIssue(g2, field, pointIssue);
            }
            if (issue instanceof PolygonQualityIssue polygonIssue) {
                drawPolygonIssue(g2, field, polygonIssue);
            }
        }
    }

    private void drawPointIssue(Graphics2D g2, MapField field, PointQualityIssue pointIssue) {
        LatLon center = Spatial.toLatLon(pointIssue.getCenter());
        double pixelSize = GoogleCoordUtils.getPixelSize(center, field.getZoomInt());
        double r = Math.max(1.5, pointIssue.getRadius() / pixelSize);

        Point2D centerScreen = field.latLonToScreen(center);
        g2.setColor(pointIssue.getColor());
        g2.fillOval(
                (int) (centerScreen.getX() - r),
                (int) (centerScreen.getY() - r),
                (int) (2 * r),
                (int) (2 * r));
    }

    private void drawPolygonIssue(Graphics2D g2, MapField field, PolygonQualityIssue polygonIssue) {
        Polygon polygon = polygonIssue.getPolygon();
        Path2D path = new Path2D.Double(Path2D.WIND_NON_ZERO);

        addRing(path, field, polygon.getExteriorRing());
        int numHoles = polygon.getNumInteriorRing();
        for (int i = 0; i < numHoles; i++) {
            addRing(path, field, polygon.getInteriorRingN(i));
        }

        g2.setColor(polygonIssue.getColor());
        g2.fill(path);
    }

    private void addRing(Path2D path, MapField field, LinearRing ring) {
        if (ring == null) {
            return;
        }

        Coordinate[] points = ring.getCoordinates();
        if (points.length == 0) {
            return;
        }
        Point2D point = field.latLonToScreen(Spatial.toLatLon(points[0]));
        path.moveTo(point.getX(), point.getY());
        for (int i = 1; i < points.length - 1; i++) {
            point = field.latLonToScreen(Spatial.toLatLon(points[i]));
            path.lineTo(point.getX(), point.getY());
        }
        path.closePath();
    }
}
