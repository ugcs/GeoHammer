package com.ugcs.geohammer.chart.gpr.axis;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.util.Arrays;
import java.util.List;

import com.ugcs.geohammer.chart.gpr.GPRChart;
import com.ugcs.geohammer.chart.gpr.ProfileField;
import com.ugcs.geohammer.format.gpr.GprFile;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.chart.ScrollableData;
import com.ugcs.geohammer.model.TraceUnit;
import com.ugcs.geohammer.model.element.BaseObject;
import com.ugcs.geohammer.model.element.BaseObjectImpl;
import com.ugcs.geohammer.model.TemplateSettings;
import com.ugcs.geohammer.model.event.TemplateUnitChangedEvent;
import com.ugcs.geohammer.util.Templates;
import javafx.application.Platform;
import javafx.geometry.Point2D;

/**
 * Horizontal ruler controller that displays trace indices only (SEG‑Y traces).
 * No distance conversion is performed.
 */
public class HorizontalRulerController {

    public static Stroke STROKE = new BasicStroke(1.0f);

    private final Model model;

    private final TraceFile file;

    public HorizontalRulerController(Model model, TraceFile file) {
        this.model = model;
        this.file = file;
    }

    public BaseObject getTB() {
        return tb;
    }

    public TraceUnit getUnit() {
        String templateName = Templates.getTemplateName(file);
        TemplateSettings templateSettings = model.getTemplateSettings();
        return templateSettings.getTraceUnit(templateName);
    }

    public void setUnit(TraceUnit traceUnit) {
        if (traceUnit == null ) {
            return;
        }

        String templateName = Templates.getTemplateName(file);
        TemplateSettings templateSettings = model.getTemplateSettings();
        templateSettings.setTraceUnit(templateName, traceUnit);
    }

    private final BaseObject tb = new BaseObjectImpl() {

        @Override
        public void drawOnCut(Graphics2D g2, ScrollableData scrollableData) {
            if (scrollableData instanceof GPRChart gprChart) {
                if (!(file instanceof GprFile)) {
                    return;
                }
                g2.setClip(null);
                Rectangle r = getRect(gprChart.getField());

                String text = getUnit().getLabel();
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
                TraceUnit[] allUnits = TraceUnit.values();
                List<TraceUnit> traceUnits = Arrays.stream(allUnits)
                        .filter(u -> u != TraceUnit.TIME).toList();

                int nextIndex = (getUnit().ordinal() + 1) % allUnits.length;
                TraceUnit nextTraceUnit = traceUnits.get(nextIndex);
                setUnit(nextTraceUnit);

                notifyTemplateUnitChange(nextTraceUnit);
                return true;
            }
            return false;
        }

        private void notifyTemplateUnitChange(TraceUnit newTraceUnit) {
            Platform.runLater(() ->
                    model.publishEvent(new TemplateUnitChangedEvent(this, file, newTraceUnit))
            );
        }
    };
}