package com.ugcs.gprvisualizer.app.auxcontrol;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.SortedMap;

import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.github.thecoldwine.sigrun.common.ext.TraceKey;
import com.ugcs.gprvisualizer.app.AppContext;
import com.ugcs.gprvisualizer.app.GPRChart;
import com.ugcs.gprvisualizer.app.ScrollableData;
import com.github.thecoldwine.sigrun.common.ext.ResourceImageHolder;
import com.ugcs.gprvisualizer.app.TraceTransform;
import com.ugcs.gprvisualizer.app.parsers.GeoData;
import com.ugcs.gprvisualizer.app.parsers.Semantic;
import com.ugcs.gprvisualizer.gpr.Model;
import com.ugcs.gprvisualizer.utils.Check;
import com.ugcs.gprvisualizer.utils.Range;
import javafx.geometry.Point2D;

public class RemoveLineButton extends PositionalObject {

	static int R_HOR = ResourceImageHolder.IMG_CLOSE_FILE.getWidth(null);
	static int R_VER = ResourceImageHolder.IMG_CLOSE_FILE.getHeight(null);

	// trace should be one of the target removal line traces
	private final TraceKey trace;

	private final Model model;

	public RemoveLineButton(TraceKey trace, Model model) {
		Check.notNull(trace);

		this.model = model;
		this.trace = trace;
	}

	@Override
	public int getTraceIndex() {
		return trace.getIndex();
	}

	@Override
	public void offset(int traceOffset) {
		trace.offset(traceOffset);
	}

	@Override
	public boolean mousePressHandle(Point2D localPoint, ScrollableData profField) {
		if (isPointInside(localPoint, profField)) {
			if (profField instanceof GPRChart gprChart) {
				SgyFile file = trace.getFile();
				SortedMap<Integer, Range> lineRanges = file.getLineRanges();
				if (lineRanges.size() == 1) {
					gprChart.close();
				} else {
					String lineHeader = GeoData.getHeaderInFile(Semantic.LINE, file);
					int lineIndex = file.getGeoData().get(trace.getIndex()).getInt(lineHeader).orElse(0);
					TraceTransform traceTransform = AppContext.getInstance(TraceTransform.class);
					traceTransform.removeLine(file, lineIndex);
				}
			}
			return true;
		}
		return false;
	}

	@Override
	public BaseObject copy(int traceOffset) {
		TraceKey newTrace = new TraceKey(trace.getFile(), trace.getIndex() - traceOffset);
		return new RemoveLineButton(newTrace, model);
	}

	@Override
	public boolean isFit(int begin, int end) {
		int traceIndex = getTraceIndex();
		return traceIndex >= begin && traceIndex <= end;
	}

	@Override
	public void drawOnCut(Graphics2D g2, ScrollableData scrollableData) {
		if (scrollableData instanceof GPRChart gprChart) {
			var profField = gprChart.getField();
			setClip(g2, profField.getClipTopMainRect());

			Rectangle rect = getRect(scrollableData);

			int leftMargin = - profField.getMainRect().width / 2;

			if (Math.abs(rect.x - leftMargin) < profField.getMainRect().width) {
				g2.drawImage(ResourceImageHolder.IMG_CLOSE_FILE, rect.x, rect.y, null);
			}
		}
	}
	
	private Rectangle getRect(ScrollableData scrollableData) {
		if (scrollableData instanceof GPRChart gprChart) {
			int x = gprChart.traceToScreen(trace.getIndex());
			Rectangle mainRect = gprChart.getField().getMainRect();
			int leftMargin = -mainRect.width / 2;
			Rectangle rect = new Rectangle(
					(Math.abs(x - leftMargin) < mainRect.width ? Math.max(x, leftMargin) : x) + 2,
					mainRect.y,
					R_HOR, R_VER);
			return rect;
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
