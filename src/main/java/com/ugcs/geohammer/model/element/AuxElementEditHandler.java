package com.ugcs.geohammer.model.element;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.ugcs.geohammer.chart.Chart;
import com.ugcs.geohammer.chart.ScrollableData;
import com.ugcs.geohammer.chart.gpr.GPRChart;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.model.event.WhatChanged;
import javafx.event.ActionEvent;
import javafx.geometry.Point2D;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ugcs.geohammer.view.ResourceImageHolder;
import com.ugcs.geohammer.model.Model;

import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Tooltip;

public class AuxElementEditHandler extends BaseObjectImpl {

	private static final Logger log = LoggerFactory.getLogger(AuxElementEditHandler.class);

	private final Model model;

	@Nullable
	private BaseObject mouseInput;

	private final Button createMarkButton = ResourceImageHolder.setButtonImage(
			ResourceImageHolder.ADD_MARK, new Button());

	private final Button removeSelectedMarkButton = ResourceImageHolder.setButtonImage(
			ResourceImageHolder.DELETE, new Button());

	private final Button removeAllMarksButton = ResourceImageHolder.setButtonImage(
			ResourceImageHolder.DELETE_ALL, new Button());

	public AuxElementEditHandler(Model model) {
		this.model = model;
		initButtons();
	}

	public List<Node> getRightPanelTools() {
		return Arrays.asList(createMarkButton, removeSelectedMarkButton, removeAllMarksButton);
	}
	
	protected void initButtons() {
		createMarkButton.setTooltip(new Tooltip("Create mark"));
		removeSelectedMarkButton.setTooltip(new Tooltip("Delete selected element"));
		removeAllMarksButton.setTooltip(new Tooltip("Delete all additional elements"));

		createMarkButton.setOnAction(this::createMark);
		removeSelectedMarkButton.setOnAction(this::removeSelectedMark);
		removeAllMarksButton.setOnAction(this::removeAllMarks);
	}

	private void createMark(ActionEvent event) {
		Chart chart = model.getFileChart(model.getCurrentFile());
		if (chart == null) {
			return;
		}
		model.createFlagOnSelection(chart);
	}

	private void removeSelectedMark(ActionEvent event) {
		Chart chart = model.getFileChart(model.getCurrentFile());
		if (chart == null) {
			return;
		}
		model.removeSelectedFlag(chart);
	}

	private void removeAllMarks(ActionEvent event) {
		Chart chart = model.getFileChart(model.getCurrentFile());
		if (chart == null || !confirmMarksRemoval()) {
			return;
		}
		model.removeAllFlags(chart);
	}

	private boolean confirmMarksRemoval() {
		Alert alert = new Alert(AlertType.CONFIRMATION);
		alert.setTitle("Remove all additional elements");
		alert.setContentText("Confirm to clear makup");

		Optional<ButtonType> result = alert.showAndWait();
		return result.isPresent() && result.get() == ButtonType.OK;
	}

	@Override
	public boolean mousePressHandle(Point2D localPoint, ScrollableData profField) {
		BaseObject element = lookupElement(localPoint, profField);
		if (element != null) {
			if (element instanceof FoundPlace flag) {
				SgyFile traceFile = flag.getTrace().getFile();
				Chart chart = model.getFileChart(traceFile);
				if (chart != null) {
					chart.selectFlag(flag);
				}
			} else {
				// select on a drag period
				element.setSelected(true);
			}
			element.mousePressHandle(localPoint, profField);
		}
		mouseInput = element;
		return mouseInput != null;
	}

	@Nullable
	private BaseObject lookupElement(Point2D localPoint, ScrollableData profField) {
		if (profField instanceof GPRChart gprChart) {
			for (BaseObject element : gprChart.getAuxElements()) {
				if (element.isPointInside(localPoint, profField)) {
					return element;
				}
			}
		}
		return null;
	}

	private boolean isAboveElement(Point2D localPoint, ScrollableData profField) {
		return lookupElement(localPoint, profField) != null;
	}

	@Override
	public boolean mouseMoveHandle(Point2D localPoint, ScrollableData profField) {
		if (mouseInput != null) {
			mouseInput.mouseMoveHandle(localPoint, profField);
			model.publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
			return true;
		} else {
			if (isAboveElement(localPoint, profField)) {
				profField.setCursor(Cursor.HAND);
			} else {
				profField.setCursor(Cursor.DEFAULT);
			}			
		}
		return false;
	}

	@Override
	public boolean mouseReleaseHandle(Point2D localPoint, ScrollableData profField) {
		if (mouseInput != null) {
			mouseInput.mouseReleaseHandle(localPoint, profField);
			if (!(mouseInput instanceof FoundPlace)) {
				mouseInput.setSelected(false);
			}
			mouseInput = null;

			profField.setCursor(Cursor.DEFAULT);
			model.publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
			return true;
		}
		return false;
	}
}