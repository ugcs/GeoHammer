package com.ugcs.geohammer.model.element;

import com.ugcs.geohammer.chart.ScrollableData;
import com.ugcs.geohammer.chart.gpr.GPRChart;
import com.ugcs.geohammer.model.TraceSample;
import javafx.geometry.Point2D;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CleverViewScrollHandler extends BaseObjectImpl {

    private static final Logger log = LoggerFactory.getLogger(CleverViewScrollHandler.class);

    private Point2D dragPoint;

    private TraceSample oldCenter;
	
	public CleverViewScrollHandler() {
	}

	@Override
	public boolean mousePressHandle(Point2D localPoint, ScrollableData profField) {
		dragPoint = localPoint;
		oldCenter = profField.screenToTraceSample(dragPoint);

    	return true;
	}

	@Override
	public boolean mouseReleaseHandle(Point2D localPoint, ScrollableData profField) {
		dragPoint = null;

        return false;
	}

	@Override
	public boolean mouseMoveHandle(Point2D point, ScrollableData profField) {
		if (dragPoint == null) {
			return false;
		}
		
		try {
    		TraceSample newCenter = profField.screenToTraceSample(point);
    		
    		int t = profField.getMiddleTrace()
    				+ oldCenter.getTrace() - newCenter.getTrace();

			profField.setMiddleTrace(t);
			if (profField instanceof GPRChart gprChart) {
				gprChart.getProfileScroll().recalc();
				gprChart.setStartSample(profField.getStartSample()
						+ oldCenter.getSample() - newCenter.getSample());
                gprChart.updateScroll();
                gprChart.repaintEvent();
			}
		} catch (Exception e) {
            log.error("Error", e);
		}
		return true;
	}	
}