package com.ugcs.geohammer.chart;

import java.util.List;

import com.ugcs.geohammer.AppContext;

import javafx.beans.value.ChangeListener;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseDragEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.jspecify.annotations.Nullable;

public final class ProfileScroll extends Canvas {

	// height of the bar
	public static final int HEIGHT = 22;

	// width of the sidebars
	private static final int SIDE_WIDTH = 14;

	// gap between the center bar and sidebars
	private static final int CENTER_MARGIN = 4;

	// vertical gap between the bar and control edges
	private static final int VERTICAL_MARGIN = 4;

	// vertical gap between the baseline and control edges
	private static final int VERTICAL_BASELINE_MARGIN = 7;

	private static final int ARC_WIDTH = 12;

	private static final int ARC_HEIGHT = 12;

	private double start = 0;

	private double finish = Double.MAX_VALUE;
	
	private double pressXInBar;

	private ChangeListener<Number> changeListener;

	private final ScrollableData scrollable;

	public void setChangeListener(ChangeListener<Number> changeListener) {
		this.changeListener = changeListener;
	}
	
	private final Bar left = new Bar() {

		@Override
		public Rectangle getBounds() {
			return new Rectangle(start - SIDE_WIDTH - CENTER_MARGIN, 0, SIDE_WIDTH, HEIGHT);
		}

		@Override
		public void move(Point2D localPoint) {
			double barStart = localPoint.getX() - pressXInBar;
			double newStart = barStart + SIDE_WIDTH + CENTER_MARGIN;
			newStart = Math.min(newStart, finish - 1);
			newStart = Math.max(newStart, 0);
			start = newStart;
			
			syncToScrollable();
			draw();

			changeListener.changed(null, null, null);
		}
	};

	private final Bar right = new Bar() {

		@Override
		public Rectangle getBounds() {
			return new Rectangle(finish + CENTER_MARGIN, 0, SIDE_WIDTH, HEIGHT);
		}

		@Override
		public void move(Point2D localPoint) {
			double barStart = localPoint.getX() - pressXInBar;
			double newFinish = barStart - CENTER_MARGIN;
			newFinish = Math.max(newFinish, start + 1);
			newFinish = Math.min(newFinish, getWidth());
			finish = newFinish;

			syncToScrollable();
			draw();

			changeListener.changed(null, null, null);
		}
	};

	private final Bar center = new Bar() {

		@Override
		public Rectangle getBounds() {
			return new Rectangle(start, 0, finish - start, HEIGHT);
		}

		@Override
		public void move(Point2D localPoint) {
			double width = finish - start;

			double barStart = localPoint.getX() - pressXInBar;
			if (barStart <= start) {
				start = Math.max(barStart, 0);
				finish = start + width;
			} else {
				finish = Math.min(barStart + width, getWidth());
				start = finish - width;
			}

			syncToScrollable();
			draw();

			changeListener.changed(null, null, null);
		}
	};

	private final List<Bar> bars = List.of(center, left, right);

	@Nullable
	private Bar dragBar;

	public ProfileScroll(ScrollableData scrollable) {
		this.scrollable = scrollable;

		addEventFilter(MouseEvent.DRAG_DETECTED, this::onMouseDragDetected);
		addEventFilter(MouseEvent.MOUSE_DRAGGED, this::onMouseDragged);
		addEventFilter(MouseDragEvent.MOUSE_DRAG_RELEASED, this::onMouseDragReleased);
		setOnMouseReleased(this::onMouseReleased);
	}

	private void onMouseDragDetected(MouseEvent event) {
		dragBar = null;

		Point2D localPoint = getLocal(event);
		for (Bar bar : bars) {
			Rectangle barBounds = bar.getBounds();
			if (barBounds.contains(localPoint)) {
				dragBar = bar;
				pressXInBar = localPoint.getX() - barBounds.getX();
			}
		}

		startFullDrag();
		setCursor(Cursor.CLOSED_HAND);
	}

	private void onMouseDragged(MouseEvent event) {
		if (dragBar != null) {
			Point2D localPoint = getLocal(event);
			dragBar.move(localPoint);
		}
	}

	private void onMouseDragReleased(MouseEvent event) {
		dragBar = null;
		setCursor(Cursor.DEFAULT);
		event.consume();
	}

	private void onMouseReleased(MouseEvent event) {
		dragBar = null;
	}

	@Override
	public void resize(double width, double height) {
		if (width >= 0 && Math.abs(getWidth() - width) > 1) {
			setWidth(width);
			setHeight(height);
			syncFromScrollable();
		}
	}

	@Override
	public boolean isResizable() {
		return true;
	}

	@Override
	public double minWidth(double height) {
		return 50; // minimum reasonable width
	}

	@Override
	public double maxWidth(double height) {
		return Double.MAX_VALUE;
	}

	@Override
	public double prefWidth(double height) {
		return getWidth();
	}

	@Override
	public double prefHeight(double width) {
		return HEIGHT;
	}

	public Point2D getLocal(MouseEvent event) {
		Point2D scenePoint = new Point2D(event.getSceneX(), event.getSceneY());
    	return sceneToLocal(scenePoint);
	}
	
	public void syncFromScrollable() {
		if (dragBar != null) {
			return; // skip updates when dragging
		}

		int numTraces = scrollable.numTraces();
		int startTrace = scrollable.getStartTrace();
		double viewWidth = scrollable.getViewWidth();
		double horizontalScale = scrollable.getHorizontalScale();

		double width = getWidth();
		double numVisibleTraces = viewWidth != 0
				? viewWidth / horizontalScale
				: numTraces;
		double scrollWidth = numTraces != 0
				? numVisibleTraces / numTraces * width
				: width;

		start = numTraces != 0
				? startTrace * width / numTraces
				: 0;
		finish = start + scrollWidth;

		draw();
	}

	private void syncToScrollable() {
		double width = getWidth();
		if (width == 0) {
			return; // update is meaningless
		}

		int numTraces = scrollable.numTraces();
		double viewWidth = scrollable.getViewWidth();

		double scrollWidth = finish - start;
		double numVisibleTraces = scrollWidth / width * numTraces;
		double horizontalScale = viewWidth != 0
				? viewWidth / (numVisibleTraces != 0 ? numVisibleTraces : 1)
				: 1;
		scrollable.setHorizontalScale(horizontalScale);

		int startTrace = (int)(start / width * numTraces);
		scrollable.setStartTrace(startTrace);
	}

	private void draw() {
		GraphicsContext gc = this.getGraphicsContext2D();
		gc.clearRect(0, 0, getWidth(), getHeight());

		Color strokeColor = AppContext.getTheme().strokeColor();
		gc.setStroke(strokeColor);

		Color fill = new Color(
				strokeColor.getRed(),
				strokeColor.getGreen(),
				strokeColor.getBlue(),
				0.38
		);
		gc.setFill(fill);

		gc.fillRect(0, VERTICAL_BASELINE_MARGIN,
				getWidth(), getHeight() - 2 * VERTICAL_BASELINE_MARGIN);
		
		Rectangle centerBounds = center.getBounds();
		gc.strokeRoundRect(
				centerBounds.getX(),
				centerBounds.getY() + VERTICAL_MARGIN,
				centerBounds.getWidth(),
				centerBounds.getHeight() - 2 * VERTICAL_MARGIN,
				ARC_WIDTH,
				ARC_HEIGHT
		);
		
		double centerX = centerBounds.getX() + centerBounds.getWidth() / 2;
		gc.strokeLine(centerX, 0, centerX, HEIGHT);
		
		Rectangle leftBounds = left.getBounds();
		gc.strokeRoundRect(
				leftBounds.getX(),
				leftBounds.getY() + VERTICAL_MARGIN,
				leftBounds.getWidth(),
				leftBounds.getHeight() - 2 * VERTICAL_MARGIN,
				ARC_WIDTH,
				ARC_HEIGHT
		);

		Rectangle rightBounds = right.getBounds();
		gc.strokeRoundRect(
				rightBounds.getX(),
				rightBounds.getY() + VERTICAL_MARGIN,
				rightBounds.getWidth(),
				rightBounds.getHeight() - 2 * VERTICAL_MARGIN,
				ARC_WIDTH,
				ARC_HEIGHT
		);
	}

	interface Bar {

		Rectangle getBounds();

		void move(Point2D localPoint);
	}
}