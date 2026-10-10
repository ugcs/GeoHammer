package com.ugcs.geohammer.map.layer;

import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.math.DouglasPeucker;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.model.MapField;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
import javafx.geometry.Point2D;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class SurveyLineFamilyLayer extends BaseLayer {

    private static final double APPROXIMATION_THRESHOLD = 1.5;

    private static final Color PRIMARY_COLOR = new Color(0x4A90E2);

    private static final Color TIE_COLOR = new Color(0x36A269);

    @Nullable
    private volatile SgyFile file;

    private volatile List<List<LatLon>> primaryLines = List.of();

    private volatile List<List<LatLon>> tieLines = List.of();

    public void setLineFamilies(SgyFile file, List<List<LatLon>> primaryLines, List<List<LatLon>> tieLines) {
        this.file = file;
        this.primaryLines = primaryLines;
        this.tieLines = tieLines;
    }

    public void clear() {
        file = null;
        primaryLines = List.of();
        tieLines = List.of();
        repaint();
    }

    public void repaint() {
        if (getRepaintListener() != null) {
            getRepaintListener().repaint();
        }
    }

    @Override
    public void draw(Graphics2D graphics, MapField field) {
        if (!isActive() || file == null) {
            return;
        }
        drawLines(graphics, field, primaryLines, PRIMARY_COLOR);
        drawLines(graphics, field, tieLines, TIE_COLOR);
    }

    private static void drawLines(Graphics2D graphics, MapField field, List<List<LatLon>> lines, Color color) {
        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(2.5f));
        for (List<LatLon> line : lines) {
            drawLine(graphics, field, line);
        }
    }

    private static void drawLine(Graphics2D graphics, MapField field, List<LatLon> line) {
        List<Point2D> points = new ArrayList<>(line.size());
        for (LatLon point : line) {
            points.add(field.latLonToScreen(point));
        }
        List<Integer> selected = DouglasPeucker.approximatePolyline(points, APPROXIMATION_THRESHOLD, 2);
        for (int i = 1; i < selected.size(); i++) {
            Point2D first = points.get(selected.get(i - 1));
            Point2D second = points.get(selected.get(i));
            graphics.drawLine((int) first.getX(), (int) first.getY(), (int) second.getX(), (int) second.getY());
        }
    }

    @EventListener
    private void onFileSelected(FileSelectedEvent event) {
        if (!Objects.equals(file, event.getFile())) {
            clear();
        }
    }
}
