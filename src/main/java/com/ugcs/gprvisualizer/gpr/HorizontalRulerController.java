package com.ugcs.gprvisualizer.gpr;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;

import com.github.thecoldwine.sigrun.common.ext.ProfileField;
import com.ugcs.gprvisualizer.app.GPRChart;
import com.ugcs.gprvisualizer.app.ScrollableData;
import com.ugcs.gprvisualizer.app.auxcontrol.BaseObject;
import com.ugcs.gprvisualizer.app.auxcontrol.BaseObjectImpl;
import javafx.geometry.Point2D;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Horizontal ruler controller that displays trace indices only (SEG‑Y traces).
 * No distance conversion is performed.
 */
public class HorizontalRulerController {

    public static Stroke STROKE = new BasicStroke(1.0f);

    public enum Unit {
        TRACES
    }

    public interface Converter {
        Pair<Integer, Integer> convert(int firstTrace, int lastTrace);

        int back(int unitValue);
    }

    private Unit unit = Unit.TRACES;

    public void setUnit(Unit unit) {
        this.unit = unit;
    }

    public Unit getUnit() {
        return unit;
    }

    public Converter getConverter() {
        return new TracesConverter();
    }

    private static class TracesConverter implements Converter {
        @Override
        public Pair<Integer, Integer> convert(int firstTrace, int lastTrace) {
            if (firstTrace <= lastTrace) {
                return new ImmutablePair<>(firstTrace, lastTrace);
            } else {
                return new ImmutablePair<>(lastTrace, firstTrace);
            }
        }

        @Override
        public int back(int unitValue) {
            return unitValue;
        }
    }

    public BaseObject getTB() {
        return tb;
    }

    private final BaseObject tb = new BaseObjectImpl() {

        @Override
        public void drawOnCut(Graphics2D g2, ScrollableData scrollableData) {
            if (scrollableData instanceof GPRChart gprChart) {
                g2.setClip(null);
                Rectangle r = getRect(gprChart.getField());

                g2.setStroke(STROKE);
                g2.setColor(Color.lightGray);
                g2.drawRoundRect(r.x, r.y, r.width, r.height, 7, 7);
            }
        }

        private Rectangle getRect(ProfileField profField) {
            Rectangle  r = profField.getInfoRect();
            return new Rectangle(profField.getVisibleStart() + r.x + 12, r.y + r.height - 35,
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
            return isPointInside(localPoint, scrollableData);
        }
    };
}