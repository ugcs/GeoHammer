package com.ugcs.geohammer.chart.gpr.axis;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import com.ugcs.geohammer.chart.gpr.GPRChart;
import com.ugcs.geohammer.format.gpr.GprFile;
import com.ugcs.geohammer.model.TraceUnit;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import com.ugcs.geohammer.util.Ticks;

public class HorizontalRulerDrawer {
    private final GPRChart field;
    private final DecimalFormat formatter = new DecimalFormat("#0.00");

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
        int totalTraces = field.numVisibleTraces();

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
                        String label = getLabelByUnit(i, traceUnit);
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
                double distance = field.getFile().getDistanceAtTrace(traceIndex);
                return formatter.format(TraceUnit.convert(distance, traceUnit));
            }
            case TRACES -> {
                return String.format("%1$3s", traceIndex);
            }
            default -> {
                return "";
            }
        }
    }

    private HorizontalRulerController getController() {
        return field.getHorizontalRulerController();
    }
}