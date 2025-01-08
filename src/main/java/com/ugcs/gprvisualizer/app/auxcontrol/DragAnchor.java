package com.ugcs.gprvisualizer.app.auxcontrol;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.util.List;

import com.github.thecoldwine.sigrun.common.ext.ProfileField;
import com.ugcs.gprvisualizer.app.GPRChart;
import com.ugcs.gprvisualizer.app.ScrollableData;
import javafx.geometry.Point2D;
import org.apache.commons.lang3.mutable.MutableInt;

import com.github.thecoldwine.sigrun.common.ext.MapField;
import com.github.thecoldwine.sigrun.common.ext.TraceSample;
import com.github.thecoldwine.sigrun.common.ext.VerticalCutPart;
//import com.ugcs.gprvisualizer.app.MouseHandler;

public class DragAnchor extends BaseObjectImpl {
	//implements BaseObject {//, MouseHandler {

	private MutableInt trace = new MutableInt();
	private MutableInt sample = new MutableInt();

	private AlignRect alignRect;
	private VerticalCutPart offset;
	
	private Image img;
	
	private Dimension dim = new Dimension(16, 16);
	
	private boolean visible = true;
	
	
	public DragAnchor(
			Image img,
			AlignRect alignRect,
			VerticalCutPart offset) {
		
		this.offset = offset;
		this.setImg(img);
		this.alignRect = alignRect;
		
		if (img != null) {
			dim = new Dimension(img.getWidth(null), img.getHeight(null));
		}
					
	}
	
	public void signal(Object obj) {
		
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
		TraceSample ts = new TraceSample(offset.localToGlobal(
				this.getTrace()), getSample());
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

	public List<BaseObject> getControls() {
		return null;
	}

	protected Image getImg() {
		return img;
	}

	protected void setImg(Image img) {
		this.img = img;
	}

	public boolean isVisible() {
		return visible;
	}

	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	@Override
	public BaseObject copy(int offset, VerticalCutPart verticalCutPart) {
		return null;
	}

	public MutableInt getSampleMtl() {
		return sample;
	}

	public MutableInt getTraceMtl() {
		return trace;
	}
}
