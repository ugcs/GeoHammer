package com.ugcs.gprvisualizer.app.auxcontrol;

import com.github.thecoldwine.sigrun.common.ext.ResourceImageHolder;
import com.github.thecoldwine.sigrun.common.ext.TraceKey;
import com.ugcs.gprvisualizer.app.GPRChart;
import com.ugcs.gprvisualizer.app.ScrollableData;
import com.ugcs.gprvisualizer.gpr.Model;
import javafx.geometry.Point2D;

import java.awt.*;

public class CloseGprChartButton extends RemoveLineButton {

    public CloseGprChartButton(Model model, TraceKey trace) {
        super(model, trace);
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
