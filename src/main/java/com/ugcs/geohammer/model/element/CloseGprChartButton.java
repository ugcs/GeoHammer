package com.ugcs.geohammer.model.element;

import com.ugcs.geohammer.view.ResourceImageHolder;
import com.ugcs.geohammer.model.TraceKey;
import com.ugcs.geohammer.chart.gpr.GPRChart;
import com.ugcs.geohammer.chart.ScrollableData;
import com.ugcs.geohammer.model.Model;
import javafx.geometry.Point2D;

import java.awt.*;

public class CloseGprChartButton extends RemoveLineButton {

    public CloseGprChartButton(TraceKey trace, Model model) {
        super(trace, model);
    }

    @Override
    public boolean mousePressHandle(Point2D localPoint, ScrollableData profField) {
        if (isPointInside(localPoint, profField) && profField instanceof GPRChart gprChart) {
            gprChart.close();
            return true;
        }
        return false;
    }

    @Override
    public void drawOnCut(Graphics2D g2, ScrollableData scrollableData) {
        g2.setClip(null);

        Rectangle rect = getRect(scrollableData);
        g2.drawImage(ResourceImageHolder.IMG_CLOSE, rect.x, rect.y, null);
    }

    private Rectangle getRect(ScrollableData scrollableData) {
        if (scrollableData instanceof GPRChart gprChart) {
            // graphics is translated to the main rect center
            Rectangle mainRect = gprChart.getField().getMainRect();
            int x = (int)(-mainRect.getWidth() / 2);
            return new Rectangle(
                    x + 2, 10,
                    R_HOR, R_VER);
        } else {
            return null;
        }
    }

    @Override
    public boolean isPointInside(Point2D localPoint, ScrollableData profField) {
        Rectangle rect = getRect(profField);
        return rect.contains(localPoint.getX(), localPoint.getY());
    }
}
