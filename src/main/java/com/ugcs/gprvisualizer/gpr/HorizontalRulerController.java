package com.ugcs.gprvisualizer.gpr;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.util.Arrays;
import java.util.List;

import com.github.thecoldwine.sigrun.common.ext.ProfileField;
import com.ugcs.gprvisualizer.app.GPRChart;
import com.ugcs.gprvisualizer.app.ScrollableData;
import com.ugcs.gprvisualizer.app.auxcontrol.BaseObject;
import com.ugcs.gprvisualizer.app.auxcontrol.BaseObjectImpl;
import com.ugcs.gprvisualizer.app.service.DistanceConverterService;
import javafx.geometry.Point2D;

/**
 * Horizontal ruler controller that displays trace indices only (SEG‑Y traces).
 * No distance conversion is performed.
 */
public class HorizontalRulerController {

    public static Stroke STROKE = new BasicStroke(1.0f);

    public BaseObject getTB() {
        return tb;
    }

    private DistanceConverterService.Unit unit = DistanceConverterService.Unit.getDefault();

    public DistanceConverterService.Unit getUnit() {
        return unit;
    }


    private final BaseObject tb = new BaseObjectImpl() {

        @Override
        public void drawOnCut(Graphics2D g2, ScrollableData scrollableData) {
            if (scrollableData instanceof GPRChart gprChart) {
                g2.setClip(null);
                Rectangle r = getRect(gprChart.getField());

                String text = unit.getLabel();
                var fm = g2.getFontMetrics();
                int textW = fm.stringWidth(text);
                int textH = fm.getAscent() + fm.getDescent();

                int padX = 10;
                int padY = 4;
                int rectW = textW + padX * 2;
                int rectH = textH + padY * 2;

                int rectX = r.x + (r.width - rectW) / 2;
                int rectY = r.y;

                g2.setColor(Color.white);
                g2.fillRoundRect(rectX, rectY, rectW, rectH, 7, 7);

                g2.setStroke(STROKE);
                g2.setColor(Color.lightGray);
                g2.drawRoundRect(rectX, rectY, rectW, rectH, 7, 7);

                int textX = rectX + (rectW - textW) / 2;
                int textY = rectY + (rectH - textH) / 2 + fm.getAscent() - 2;

                g2.setColor(Color.black);
                g2.drawString(text, textX, textY);
            }
        }

        private Rectangle getRect(ProfileField profField) {
            Rectangle r = profField.getInfoRect();
            Rectangle mainRect = profField.getMainRect();
            return new Rectangle(profField.getVisibleStart() + (r.x / 2), mainRect.height + profField.getBottomRuleRect().height + 35,
                    r.width - 15, 20);
        }

        @Override
        public boolean isPointInside(Point2D localPoint, ScrollableData scrollableData) {
            if (scrollableData instanceof GPRChart gprChart) {
                Rectangle rect = getRect(gprChart.getField());
                return rect.contains(localPoint.getX(), localPoint.getY());
            }
            return false;
        }

        @Override
        public boolean mousePressHandle(Point2D localPoint, ScrollableData scrollableData) {
            if (isPointInside(localPoint, scrollableData)) {
                List<DistanceConverterService.Unit> units = Arrays.stream(DistanceConverterService.Unit.values())
                        .filter(u -> u != DistanceConverterService.Unit.TIME).toList();

                int currentIndex = units.indexOf(unit);
                int nextIndex = (currentIndex + 1) % units.size();
                unit = units.get(nextIndex);
                return true;
            }
            return false;
        }
    };
}