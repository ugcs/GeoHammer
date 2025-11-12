package com.ugcs.geohammer.model.element;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;

import com.ugcs.geohammer.chart.gpr.ProfileField;
import com.ugcs.geohammer.chart.gpr.GPRChart;
import com.ugcs.geohammer.chart.ScrollableData;
import javafx.geometry.Point2D;
import org.apache.commons.lang3.mutable.MutableInt;

import com.ugcs.geohammer.model.MapField;
import com.ugcs.geohammer.model.TraceSample;

public class DragAnchor extends BaseObjectImpl {

	private MutableInt trace = new MutableInt();
	private MutableInt sample = new MutableInt();

	private AlignRect alignRect;

	private Image img;
	
	private Dimension dim = new Dimension(16, 16);
	
	private boolean visible = true;
	
	public DragAnchor(Image img, AlignRect alignRect) {
		this.img = img;
		this.alignRect = alignRect;
		
		if (img != null) {
			dim = new Dimension(img.getWidth(null), img.getHeight(null));
		}
	}

	@Override
	public void drawOnMap(Graphics2D g2, MapField mapField) {
		//is not visible on the map view
	}

	@Override
	public void drawOnCut(Graphics2D g2, ScrollableData scrollableData) {
		if (scrollableData instanceof GPRChart gprChart) {
			if (!isVisible()) {
				return;
			}

			ProfileField profField = gprChart.getField();
			g2.setClip(profField.getClipMainRect().x,
					profField.getClipMainRect().y,
					profField.getClipMainRect().width,
					profField.getClipMainRect().height);

			Rectangle rect = getRect(gprChart);
			realDraw(g2, rect);
		}
	}

	protected void realDraw(Graphics2D g2, Rectangle rect) {
		if (getImg() == null) {
			g2.setColor(Color.MAGENTA);
			g2.fillOval(rect.x, rect.y, rect.width, rect.height);
		} else {
			g2.drawImage(getImg(), rect.x, rect.y, null);
		}
	}

	private Rectangle getRect(ScrollableData profField) {
		TraceSample ts = new TraceSample(this.getTrace(), getSample());
		Point2D scr = profField.traceSampleToScreen(ts);
		Rectangle rect = alignRect.getRect(scr, dim);
		return rect;
	}

	@Override
	public boolean isPointInside(Point2D localPoint, ScrollableData profField) {
		if (!isVisible()) {
			return false;
		}		
		
		Rectangle rect = getRect(profField);
		
		return rect.contains(localPoint.getX(), localPoint.getY());
	}

	@Override
	public boolean mousePressHandle(Point2D point, MapField field) {
		return false;
	}

	@Override
	public boolean mousePressHandle(Point2D localPoint, ScrollableData profField) {
		if (isPointInside(localPoint, profField)) {
			signal(null);
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseReleaseHandle(Point2D localPoint, ScrollableData profField) {
		return true;
	}

	@Override
	public boolean mouseMoveHandle(Point2D point, ScrollableData profField) {
		if (!isVisible()) {
			return false;
		}

		TraceSample ts = profField.screenToTraceSample(point); // , offset);
		setTrace(ts.getTrace());
		setSample(ts.getSample());
		
		signal(null);
		return true;
	}
	
	public int getTrace() {
		return trace.getValue();
	}

	public void setTrace(int t) {
		trace.setValue(t);
	}
	
	public int getSample() {
		return sample.getValue();
	}

	public void setSample(int s) {
		sample.setValue(s);
	}

	protected Image getImg() {
		return img;
	}

	public boolean isVisible() {
		return visible;
	}

	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	public MutableInt getSampleMtl() {
		return sample;
	}

	public MutableInt getTraceMtl() {
		return trace;
	}
}
