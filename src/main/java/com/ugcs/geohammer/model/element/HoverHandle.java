package com.ugcs.geohammer.model.element;

import com.ugcs.geohammer.chart.ScrollableData;
import com.ugcs.geohammer.chart.gpr.GPRChart;
import javafx.geometry.Point2D;
import javafx.scene.control.Tooltip;


public abstract class HoverHandle extends BaseObjectImpl {

    private final Tooltip tooltip;

    protected HoverHandle(Tooltip tooltip) {
        this.tooltip = tooltip;
    }

    @Override
    public boolean mousePressHandle(Point2D localPoint, ScrollableData profField) {
        if (isPointInside(localPoint, profField)) {
            tooltip.hide();
            return true;
        }
        return false;
    }

	@Override
	public boolean mouseHoverHandle(Point2D point, ScrollableData profField) {
		if (canShowTooltip(profField) && profField instanceof GPRChart gprChart) {
			if (!tooltip.isShowing()) {
				Point2D canvas = gprChart.localToCanvas(point);
				Point2D screen = gprChart.getCanvas().localToScreen(canvas.getX(), canvas.getY());
				tooltip.show(gprChart.getCanvas(), screen.getX() + 12, screen.getY() + 12);
			}
		} else {
			tooltip.hide();
		}
		return true;
	}

	@Override
	public boolean mouseHoverEndHandle(Point2D point, ScrollableData profField) {
		tooltip.hide();
		return true;
	}

    protected boolean canShowTooltip(ScrollableData scrollable) {
        return true;
    }
}
