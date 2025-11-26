package com.ugcs.geohammer;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import com.ugcs.geohammer.chart.Chart;
import com.ugcs.geohammer.chart.ProfileScroll;
import com.ugcs.geohammer.chart.gpr.GPRChart;
import com.ugcs.geohammer.chart.gpr.ProfileField;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.model.event.FileClosedEvent;
import com.ugcs.geohammer.model.event.FileOpenedEvent;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
import com.ugcs.geohammer.model.event.WhatChanged;
import com.ugcs.geohammer.service.TraceTransform;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ugcs.geohammer.view.ResourceImageHolder;
import com.ugcs.geohammer.model.Model;

import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

@Component
public class ProfileView implements InitializingBean {

	private final Model model;
	private final Navigator navigator;
	private final Saver saver;
	private final TraceTransform traceTransform;

	private final ToolBar toolBar = new ToolBar();
	
	private final Button zoomInBtn = ResourceImageHolder.setButtonImage(ResourceImageHolder.ZOOM_IN, new Button());
	private final Button zoomOutBtn = ResourceImageHolder.setButtonImage(ResourceImageHolder.ZOOM_OUT, new Button());
	private final Button fitBtn = ResourceImageHolder.setButtonImage(ResourceImageHolder.FIT_CHART, new Button());

	private final Button cropSamples = ResourceImageHolder.setButtonImage(ResourceImageHolder.CROP_SAMPLES, new Button());

	private SgyFile currentFile;

	public ProfileView(Model model, Navigator navigator,
                       Saver saver, TraceTransform traceTransform) {
		this.model = model;
        this.navigator = navigator;
		this.saver = saver;
		this.traceTransform = traceTransform;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		prepareToolbar();
		zoomInBtn.setTooltip(new Tooltip("Zoom in"));
		zoomOutBtn.setTooltip(new Tooltip("Zoom out"));
		zoomInBtn.setOnAction(this::zoomIn);
		zoomOutBtn.setOnAction(this::zoomOut);

		fitBtn.setTooltip(new Tooltip("Fit current chart to window"));
		fitBtn.setOnAction(this::fitCurrentFile);

		cropSamples.setTooltip(new Tooltip("Crop samples"));
		cropSamples.setOnAction(this::cropSamples);
	}

	private void fitCurrentFile(ActionEvent actionEvent) {
		Chart chart = model.getChart(currentFile);
		if (chart != null) {
			chart.zoomToFit();
		}
	}

	private void zoomIn(ActionEvent event) {
		Chart chart = model.getChart(currentFile);
		if (chart != null) {
			chart.zoomIn();
		}
	}

	private void zoomOut(ActionEvent event) {
		Chart chart = model.getChart(currentFile);
		if (chart != null) {
			chart.zoomOut();
		}
	}

	private void cropSamples(ActionEvent actionEvent) {
		if (currentFile instanceof TraceFile traceFile) {
			GPRChart chart = model.getGprChart(traceFile);
			if (chart != null) {
				ProfileField profileField = chart.getField();
				Settings profileSettings = profileField.getProfileSettings();

				int offset = profileSettings.getLayer();
				int length = profileSettings.hpage;
				traceTransform.cropGprSamples(traceFile, offset, length);
			}
		}
	}

	private void prepareToolbar() {
		toolBar.getItems().addAll(saver.getToolNodes());
		toolBar.getItems().add(getSpacer());
		
		toolBar.getItems().addAll(model.getAuxEditHandler().getRightPanelTools());
		toolBar.getItems().add(getSpacer());

		toolBar.getItems().addAll(navigator.getToolNodes());
		toolBar.getItems().add(getSpacer());

		toolBar.getItems().add(zoomInBtn);
		toolBar.getItems().add(zoomOutBtn);
		toolBar.getItems().add(fitBtn);
		toolBar.getItems().add(getSpacer());

		toolBar.getItems().add(cropSamples);

		enableToolbar();
	}

	private void enableToolbar() {
		toolBar.getItems().forEach(toolBarItem -> toolBarItem.setDisable(!model.isActive()));

		// open file
		toolBar.getItems().getFirst().setDisable(false);

		// crop samples
		Chart chart = model.getChart(currentFile);
		cropSamples.setDisable(!(chart instanceof GPRChart));
	}

	private VBox center;

	private VBox profileScrollContainer;

	//center
	public VBox getCenter() {
		if (center == null) {
			center = new VBox();
			center.setMinWidth(100);

			ScrollPane centerScrollPane = new ScrollPane();
			centerScrollPane.setStyle("-fx-focus-color: transparent; -fx-faint-focus-color: transparent;");

			profileScrollContainer = new VBox();
			profileScrollContainer.setPadding(Insets.EMPTY);

			center.getChildren().addAll(toolBar, profileScrollContainer, centerScrollPane);

			centerScrollPane.setFitToWidth(true);
			centerScrollPane.setFitToHeight(true);
			centerScrollPane.setContent(model.getChartsContainer());
		}

		return center;
	}

	public List<Node> getRight(TraceFile file) {
		GPRChart gprChart = model.getGprChart(file);
		if (gprChart == null) {
			return List.of();
		}
		var contrastNode = gprChart.getContrastSlider().produce();
		return List.of(contrastNode);
	}

	@EventListener
	private void somethingChanged(WhatChanged changed) {
		if (changed.isJustdraw() && currentFile instanceof TraceFile traceFile) {
			GPRChart gprChart = model.getGprChart(traceFile);
			if (gprChart != null) {
				gprChart.updateScroll();
				gprChart.repaintEvent();
			}
		}
	}

	@EventListener
	private void fileClosed(FileClosedEvent event) {
		SgyFile closedFile = event.getFile();
        if (closedFile == null) {
            return;
        }

        model.getFileManager().removeFile(closedFile);
        if (closedFile.equals(currentFile)) {
            currentFile = null;
        }

		model.removeChart(closedFile);
		model.updateAuxElements();
		model.publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
		model.publishEvent(new WhatChanged(this, WhatChanged.Change.traceValues));
	}

	@EventListener
	private void fileOpened(FileOpenedEvent event) {
		enableToolbar();
	}

	private void setProfileScroll(ProfileScroll profileScroll) {
		ObservableList<Node> container = profileScrollContainer.getChildren();
		if (profileScroll == null) {
			container.clear();
			return;
		}
		if (!container.isEmpty()) {
			if (container.getFirst() instanceof ProfileScroll active) {
				if (Objects.equals(profileScroll, active)) {
					// already active, nothing to change
					active.setVisible(true);
					return;
				}
			}
			container.clear();
		}
		profileScroll.setVisible(true);
		VBox.setMargin(profileScroll, Insets.EMPTY);
		container.add(profileScroll);
	}

	private ProfileScroll getFileProfileScroll(SgyFile file) {
        var chart = model.getChart(file);
        return chart != null
                ? chart.getProfileScroll()
                : null;
	}

	@EventListener
	private void fileSelected(FileSelectedEvent event) {
		ProfileScroll profileScroll = getFileProfileScroll(event.getFile());
		setProfileScroll(profileScroll);

		if (event.getFile() != null) {
			currentFile = event.getFile();
			if (currentFile instanceof TraceFile traceFile) {
				var gprChart = model.getGprChart(traceFile);
				if (gprChart != null) {
					ChangeListener<Number> sp2SizeListener = (observable, oldValue, newValue) -> {
						if (Math.abs(newValue.intValue() - oldValue.intValue()) > 1) {
							updateGprChartSize(gprChart);
						}
					};
					center.widthProperty().addListener(sp2SizeListener);
					//((VBox) gprChart.getRootNode()).widthProperty().addListener(sp2SizeListener);
					((VBox) gprChart.getRootNode()).heightProperty().addListener(sp2SizeListener);
				}
			}
		}

		Platform.runLater(this::enableToolbar);
	}

	private void updateGprChartSize(GPRChart chart) {
		if (chart == null) {
			return;
		}
		chart.setSize(
				(int) (center.getWidth() - 21),
				(int) (Math.max(400, ((VBox) chart.getRootNode()).getHeight()) - 4));
	}

	private Region getSpacer() {
		Region r3 = new Region();
		r3.setPrefWidth(7);
		return r3;
	}
}
