package com.ugcs.geohammer.model.element;

import com.ugcs.geohammer.chart.ScrollableData;
import com.ugcs.geohammer.chart.gpr.GPRChart;
import javafx.geometry.Point2D;
import javafx.scene.control.Tooltip;


public abstract class HoverHandle extends BaseObjectImpl {

    private final Tooltip tooltip;

    private boolean dragging;

    protected HoverHandle(Tooltip tooltip) {
        this.tooltip = tooltip;
    }

    @Override
    public boolean mousePressHandle(Point2D localPoint, ScrollableData profField) {
        if (isPointInside(localPoint, profField)) {
            dragging = true;
            tooltip.hide();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleaseHandle(Point2D localPoint, ScrollableData profField) {
        dragging = false;
        return false;
    }

    @Override
    public boolean mouseMoveHandle(Point2D localPoint, ScrollableData scrollable) {
        if (dragging) {
            return handleDrag(localPoint, scrollable);
        }
        if (canShowTooltip(scrollable) && scrollable instanceof GPRChart gprChart) {
            if (!tooltip.isShowing()) {
                Point2D canvas = gprChart.localToCanvas(localPoint);
                Point2D screen = gprChart.getCanvas().localToScreen(canvas.getX(), canvas.getY());
                tooltip.show(gprChart.getCanvas(), screen.getX() + 12, screen.getY() + 12);
            }
        } else {
            tooltip.hide();
        }
        return true;
    }

    @Override
    public void onHoverEnd(ScrollableData profField) {
        tooltip.hide();
    }

    protected boolean canShowTooltip(ScrollableData scrollable) {
        return true;
    }

    protected boolean handleDrag(Point2D localPoint, ScrollableData scrollable) {
        return false;
    }
}
