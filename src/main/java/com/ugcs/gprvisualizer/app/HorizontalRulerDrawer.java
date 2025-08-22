package com.ugcs.gprvisualizer.app;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import com.ugcs.gprvisualizer.gpr.HorizontalRulerController;
import com.ugcs.gprvisualizer.gpr.HorizontalRulerController.Converter;
import com.ugcs.gprvisualizer.utils.Ticks;

public class HorizontalRulerDrawer {

    private final GPRChart field;

    public HorizontalRulerDrawer(GPRChart field) {
        this.field = field;
    }

    public void draw(Graphics2D g2) {
        Rectangle rect = field.getField().getBottomRuleRect();

        int firstTrace = field.getFirstVisibleTrace();
        int lastTrace = field.getLastVisibleTrace();

        Converter converter = getConverter();
        Pair<Integer, Integer> p = converter.convert(firstTrace, lastTrace);
        int first = p.getLeft();
        int last = p.getRight();

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
        FontMetrics fm = g2.getFontMetrics();

        int sz = 21;
        for (int step : steps) {
            int s = (int) Math.ceil((double) first / step) * step;
            int f = last / step * step;

            for (int i = s; i <= f; i += step) {
                int traceIndex = converter.back(i);

                int x = rect.x + (int)(traceIndex * field.getHScale()) - (int)(firstTrace * field.getHScale());

                if (x >= rect.x && x <= rect.x + rect.width) {
                    g2.setColor(Color.lightGray);
                    g2.drawLine(x, rect.y, x, rect.y + sz);

                    if (step == tick) {
                        String label = String.format("%1$3s", i);
                        int w = fm.stringWidth(label);
                        g2.setColor(Color.darkGray);
                        g2.drawString(label, x - w / 2, rect.y + rect.height - 4);
                    }
                }
            }
            sz = sz * 2 / 3;
        }
    }

    private HorizontalRulerController.Converter getConverter() {
        return field.getHorizontalRulerController().getConverter();
    }
}