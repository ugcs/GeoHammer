package com.ugcs.gprvisualizer.app.auxcontrol;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.List;
import java.util.Optional;

import com.ugcs.gprvisualizer.app.GPRChart;
import com.ugcs.gprvisualizer.app.ScrollableData;
import com.github.thecoldwine.sigrun.common.ext.ResourceImageHolder;
import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.github.thecoldwine.sigrun.common.ext.VerticalCutPart;
import com.ugcs.gprvisualizer.app.AppContext;
import com.ugcs.gprvisualizer.app.events.FileClosedEvent;
import com.ugcs.gprvisualizer.event.FileOpenedEvent;
import com.ugcs.gprvisualizer.gpr.Model;
import javafx.geometry.Point2D;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;

public class RemoveFileButton extends BaseObjectWithModel {

	static int R_HOR = ResourceImageHolder.IMG_CLOSE_FILE.getWidth(null);
	static int R_VER = ResourceImageHolder.IMG_CLOSE_FILE.getHeight(null);

	private final int traceInFile;
	private final VerticalCutPart offset;
	private final SgyFile sgyFile;
	
	public RemoveFileButton(int trace, VerticalCutPart offset, SgyFile sgyFile, Model model) {
		super(model);
		this.offset = offset;
		this.traceInFile = trace;
		this.sgyFile = sgyFile;
	}

	@Override
	public boolean mousePressHandle(Point2D localPoint, ScrollableData profField) {
		if (isPointInside(localPoint, profField)) {
			Alert alert = new Alert(AlertType.CONFIRMATION);
			alert.setTitle("Close file");
			alert.setContentText(
					(sgyFile.isUnsaved() ? "File is not saved\n" : "")
					+ "Confirm to close file");
			 
			Optional<ButtonType> result = alert.showAndWait();
			 
			if ((result.isPresent()) && (result.get() == ButtonType.OK)) {
				model.publishEvent(new FileClosedEvent(this, sgyFile));
			}
			return true;
		}
		return false;
	}

	@Override
	public BaseObject copy(int traceoffset, VerticalCutPart verticalCutPart) {
		RemoveFileButton result = new RemoveFileButton(
				traceInFile, verticalCutPart, sgyFile, model);
		return result;
	}

	@Override
	public boolean isFit(int begin, int end) {
		return traceInFile >= begin && traceInFile <= end;
	}

	@Override
	public void drawOnCut(Graphics2D g2, ScrollableData scrollableData) {
		if (scrollableData instanceof GPRChart gprChart) {
			var profField = gprChart.getField();
			setClip(g2, profField.getClipTopMainRect());

			Rectangle rect = getRect(scrollableData);

			int leftMargin = - profField.getMainRect().width / 2;

			if (Math.abs(rect.x - leftMargin) < profField.getMainRect().width) {
				//var x = Math.max(rect.x, leftMargin);
				g2.translate(rect.x, rect.y);
				g2.drawImage(ResourceImageHolder.IMG_CLOSE_FILE, 0, 0, null);
				g2.translate(-rect.x, -rect.y);
			}
		}
	}
	
	private Rectangle getRect(ScrollableData scrollableData) {
		if (scrollableData instanceof GPRChart gprChart) {
			int x = gprChart.traceToScreen(offset.localToGlobal(traceInFile));
			int leftMargin = - gprChart.getField().getMainRect().width / 2;
			Rectangle rect = new Rectangle(Math.abs(x - leftMargin) < gprChart.getField().getMainRect().width ? Math.max(x, leftMargin) : x, 0,
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
