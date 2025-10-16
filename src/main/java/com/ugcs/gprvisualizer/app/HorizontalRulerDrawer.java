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
import com.ugcs.gprvisualizer.app.parcers.Semantic;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import com.ugcs.gprvisualizer.gpr.HorizontalRulerController;
import com.ugcs.gprvisualizer.utils.Ticks;

import javax.annotation.Nonnull;

public class HorizontalRulerDrawer {
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
        int totalTraces = field.getVisibleNumberOfTrace();

        Pair<Integer, Integer> pair;
        if (firstTrace <= lastTrace) {
            pair = new ImmutablePair<>(firstTrace, lastTrace);
        } else {
            pair = new ImmutablePair<>(lastTrace, firstTrace);
        }
        int first = pair.getLeft();
        int last = pair.getRight();

        FontMetrics fontMetrics = g2.getFontMetrics();
        TraceUnit traceUnit = getController().getUnit();
        String sampleLabel = getLabelByUnit(first, traceUnit);
        int labelWidth = fontMetrics.stringWidth(sampleLabel);
        int minPixelSpacing = labelWidth + 20;

        double minTraceSpacing = minPixelSpacing / field.getHScale();
        int minTick = Math.max(1, (int) Math.ceil(minTraceSpacing));

        int baseTick = Math.max(1, (int) Ticks.getPrettyTick(first, last, 10));
        int tick = Math.max(minTick, baseTick);

        List<Integer> steps = new ArrayList<>();
        steps.add(tick);
        if (tick % 2 == 0) {
            steps.add(tick / 2);
        }
        if (tick % 10 == 0) {
            steps.add(tick / 10);
        }

        g2.setFont(new Font("Arial", Font.PLAIN, 11));

        int sz = 21;
        for (int step : steps) {
            int startIndex = (int) Math.ceil((double) first / step) * step;
            int endIndex = last / step * step;

            for (int i = startIndex; i <= endIndex; i += step) {
                int x;
                if (firstTrace == 0 && totalTraces - lastTrace > 0) {
                    x = rect.x + (int) Math.round((i - firstTrace + totalTraces - lastTrace) * field.getHScale());
                } else {
                    x = rect.x + (int) Math.round((i - firstTrace) * field.getHScale());
                }

                if (x >= rect.x && x <= rect.x + rect.width) {
                    g2.setColor(Color.lightGray);
                    g2.drawLine(x, rect.y, x, rect.y + sz);

                    if (step == tick) {
                        TraceUnit unit = getController().getUnit();
                        String label = getLabelByUnit(i, unit);
                        int width = fontMetrics.stringWidth(label);
                        g2.setColor(Color.darkGray);
                        g2.drawString(label, x - width / 2, rect.y + rect.height - 4);
                    }
                }
            }
            sz = sz * 2 / 3;
        }
    }

    private String getLabelByUnit(int traceIndex, TraceUnit traceUnit) {
        switch (traceUnit) {
            case METERS, KILOMETERS, MILES, FEET -> {
                double distance = getDistanceAtTrace(traceIndex, traceUnit);
                return formatter.format(distance);
            }
            case TRACES -> {
                return String.format("%1$3s", traceIndex);
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

        String lineHeader = GeoData.getHeader(Semantic.LINE, null);
        for (int i = 1; i < geoData.size(); i++) {
            LatLon previous = geoData.get(i - 1).getLatLon();
            LatLon current = geoData.get(i).getLatLon();

            Optional<Integer> previousLineIndex = geoData.get(i - 1).getInt(lineHeader);
            Optional<Integer> currentLineIndex = geoData.get(i).getInt(lineHeader);

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

    private double getDistanceAtTrace(int traceIndex, TraceUnit distanceTraceUnit) {
        List<GeoData> geoData = field.getFile().getGeoData();
        if (cumulativeDistances.size() != geoData.size()) {
            initializeCumulativeDistances();
        }

        if (geoData.isEmpty() || traceIndex < 0 || traceIndex >= geoData.size()) {
            return 0.0;
        }
        return TraceUnit.convert(cumulativeDistances.get(traceIndex), distanceTraceUnit);
    }

    private HorizontalRulerController getController() {
        return field.getHorizontalRulerController();
    }
}