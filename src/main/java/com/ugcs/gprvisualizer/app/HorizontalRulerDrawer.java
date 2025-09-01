package com.ugcs.gprvisualizer.app;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.github.thecoldwine.sigrun.common.ext.GprFile;
import com.github.thecoldwine.sigrun.common.ext.LatLon;
import com.ugcs.gprvisualizer.app.parcers.GeoData;
import com.ugcs.gprvisualizer.app.service.DistanceConverterService;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import com.ugcs.gprvisualizer.gpr.HorizontalRulerController;
import com.ugcs.gprvisualizer.utils.Ticks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

public class HorizontalRulerDrawer {

    private static final Logger log = LoggerFactory.getLogger(HorizontalRulerDrawer.class);

    private final GPRChart field;
    private final DecimalFormat formatter = new DecimalFormat();
    @Nonnull
    private final List<Double> cumulativeDistances = new ArrayList<>();

    public HorizontalRulerDrawer(GPRChart field) {
        this.field = field;
    }

    public void draw(Graphics2D g2) {
        if (!(field.getFile() instanceof GprFile)) {
            return;
        }
        Rectangle originalRect = field.getField().getBottomRuleRect();
        Rectangle rect = new Rectangle(originalRect);
        rect.setSize(originalRect.width, originalRect.height - 30);

        int firstTrace = field.getFirstVisibleTrace();
        int lastTrace = field.getLastVisibleTrace();

        Pair<Integer, Integer> pair;
        if (firstTrace <= lastTrace) {
            pair = new ImmutablePair<>(firstTrace, lastTrace);
        } else {
            pair = new ImmutablePair<>(lastTrace, firstTrace);
        }
        int first = pair.getLeft();
        int last = pair.getRight();

        int tick = Math.max(1, (int) Ticks.getPrettyTick(first, last, 10));

        List<Integer> steps = new ArrayList<>();
        steps.add(tick);
        if (tick % 2 == 0) {
            steps.add(tick / 2);
        }
        if (tick % 10 == 0) {
            steps.add(tick / 10);
        }

        g2.setFont(new Font("Arial", Font.PLAIN, 11));
        FontMetrics fontMetrics = g2.getFontMetrics();

        int sz = 21;
        for (int step : steps) {
            int startIndex = (int) Math.ceil((double) first / step) * step;
            int endIndex = last / step * step;

            for (int i = startIndex; i <= endIndex; i += step) {

                int x = rect.x + (int) Math.round((i - firstTrace) * field.getHScale());

                if (x >= rect.x && x <= rect.x + rect.width) {
                    g2.setColor(Color.lightGray);
                    g2.drawLine(x, rect.y, x, rect.y + sz);

                    if (step == tick) {
                        DistanceConverterService.Unit unit = getController().getUnit();
                        String label = getLabelByUnit(i, unit);
                        int labelWidth = fontMetrics.stringWidth(label);
                        g2.setColor(Color.darkGray);
                        g2.drawString(label, x - labelWidth / 2, rect.y + rect.height - 4);
                    }
                }
            }
            sz = sz * 2 / 3;
        }
    }

    private String getLabelByUnit(int traceIndex, DistanceConverterService.Unit unit) {
        switch (unit) {
            case METERS, KILOMETERS, MILES, FEET -> {
                if (!unit.isDistanceBased()) {
                    log.warn("Selected unit {} is not distance-based.", unit);
                    return "";
                }
                double distance = getDistanceAtTrace(traceIndex, unit);
                return formatter.format(distance);
            }
            case TRACES -> {
                return String.format("%1$3s", traceIndex);
            }
            case TIME -> {
                log.warn("TIME unit is not supported for the SEG-Y files.");
                return "";
            }
            default -> {
                return "";
            }
        }
    }

    private void initializeCumulativeDistances() {
        List<GeoData> geoData = field.getFile().getGeoData();
        cumulativeDistances.clear();

        if (geoData.isEmpty()) {
            return;
        }

        cumulativeDistances.add(0.0);

        for (int i = 1; i < geoData.size(); i++) {
            LatLon previous = geoData.get(i - 1).getLatLon();
            LatLon current = geoData.get(i).getLatLon();

            Optional<Integer> previousLineIndex = geoData.get(i - 1).getLineIndex();
            Optional<Integer> currentLineIndex = geoData.get(i).getLineIndex();

            // For different lines, reset distance to 0
            if (previousLineIndex.isPresent() && currentLineIndex.isPresent()) {
                if (!previousLineIndex.get().equals(currentLineIndex.get())) {
                    cumulativeDistances.add(0.0);
                    continue;
                }
            }

            double previousDistance = cumulativeDistances.get(i - 1);
            double segmentDistance = previous.getDistance(current);
            cumulativeDistances.add(previousDistance + segmentDistance);
        }
    }

    private double getDistanceAtTrace(int traceIndex, DistanceConverterService.Unit distanceUnit) {
        List<GeoData> geoData = field.getFile().getGeoData();
        if (cumulativeDistances.size() != geoData.size()) {
            initializeCumulativeDistances();
        }

        if (geoData.isEmpty() || traceIndex < 0 || traceIndex >= geoData.size()) {
            return 0.0;
        }
        return DistanceConverterService.convert(cumulativeDistances.get(traceIndex), distanceUnit);
    }

    private HorizontalRulerController getController() {
        return field.getHorizontalRulerController();
    }
}