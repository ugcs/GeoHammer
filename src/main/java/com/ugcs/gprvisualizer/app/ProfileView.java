package com.ugcs.gprvisualizer.app;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import com.github.thecoldwine.sigrun.common.ext.CsvFile;
import com.github.thecoldwine.sigrun.common.ext.GprFile;
import com.github.thecoldwine.sigrun.common.ext.ProfileField;
import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.ugcs.gprvisualizer.app.events.FileClosedEvent;
import com.ugcs.gprvisualizer.event.FileOpenedEvent;
import com.ugcs.gprvisualizer.event.FileSelectedEvent;
import com.ugcs.gprvisualizer.event.WhatChanged;
import com.ugcs.gprvisualizer.gpr.Settings;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.github.thecoldwine.sigrun.common.ext.ResourceImageHolder;
import com.ugcs.gprvisualizer.gpr.Model;

import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

@Component
public class ProfileView implements InitializingBean {

	private final Model model;
	private final Navigator navigator;
	private final Saver saver;
	private final SampleCutter sampleCutter;

	private final ToggleButton auxModeBtn = new ToggleButton("aux");

	private final ToolBar toolBar = new ToolBar();
	
	private final Button zoomInBtn = ResourceImageHolder.setButtonImage(ResourceImageHolder.ZOOM_IN, new Button());
	private final Button zoomOutBtn = ResourceImageHolder.setButtonImage(ResourceImageHolder.ZOOM_OUT, new Button());
	private final Button fitBtn = ResourceImageHolder.setButtonImage(ResourceImageHolder.FIT_CHART, new Button());

	private final Button cropSamples = ResourceImageHolder.setButtonImage(ResourceImageHolder.CROP_SAMPLES, new Button());

	private SgyFile currentFile;

	public ProfileView(Model model, Navigator navigator,
                       Saver saver, SampleCutter sampleCutter) {
		this.model = model;
        this.navigator = navigator;
		this.saver = saver;
		this.sampleCutter = sampleCutter;
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
		if (currentFile instanceof CsvFile csvFile) {
			model.getChart(csvFile).ifPresent(SensorLineChart::zoomToFit);
		} else {
			var chart = model.getProfileField(currentFile);
			chart.fitFull();
			model.publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
		}
	}

	private void zoomIn(ActionEvent event) {
		if (currentFile instanceof CsvFile csvFile) {
			Optional<SensorLineChart> chart = model.getChart(csvFile);
			chart.ifPresent(SensorLineChart::zoomIn);
		} else {
			if (currentFile != null) {
				model.getProfileField(currentFile).zoom(1, false); //zoom(1, width / 2, height / 2, false);
			}
		}
	}

	private void zoomOut(ActionEvent event) {
		if (currentFile instanceof CsvFile csvFile) {
			Optional<SensorLineChart> chart = model.getChart(csvFile);
			chart.ifPresent(SensorLineChart::zoomOut);
		} else {
			if (currentFile != null) {
				model.getProfileField(currentFile).zoom(-1, false); //zoom(-1, width / 2, height / 2, false);
			}
		}
	}

	private void cropSamples(ActionEvent actionEvent) {
		Chart chart = model.getFileChart(currentFile);
		if (chart instanceof GPRChart gprChart) {
			ProfileField profileField = gprChart.getField();
			Settings profileSettings = profileField.getProfileSettings();

			int offset = profileSettings.getLayer();
			int length = profileSettings.hpage;
			sampleCutter.cropGprSamples(gprChart, offset, length);
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
		Chart chart = model.getFileChart(currentFile);
		cropSamples.setDisable(!(chart instanceof GPRChart));
	}

	private VBox center;

	private VBox profileScrollContainer;

	//center
	public Node getCenter() {
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

	public List<Node> getRight(SgyFile file) {
		var contrastNode = model.getProfileField(file).getContrastSlider().produce();
		return List.of(contrastNode);
	}

	@EventListener
	private void somethingChanged(WhatChanged changed) {
		if (changed.isJustdraw() && currentFile != null && !(currentFile instanceof CsvFile)
				&& model.getProfileField(currentFile) instanceof GPRChart gprChart) {
			gprChart.updateScroll();
			gprChart.repaintEvent();
		}
	}

	@EventListener
	private void fileClosed(FileClosedEvent event) {
		SgyFile closedFile = event.getSgyFile();

		if (model.getProfileField(closedFile) instanceof GPRChart gprPane) {
			var vbox = (VBox) gprPane.getRootNode();

			gprPane.getField().removeSgyFile(closedFile);
			model.getFileManager().removeFile(closedFile);

			if (gprPane.getField().getGprTraces().isEmpty()) {
				gprPane.getProfileScroll().setVisible(false);
				model.getChartsContainer().getChildren().remove(vbox);
				currentFile = null;
				model.publishEvent(new FileSelectedEvent(this, currentFile));
			} else {
				if (currentFile != null && currentFile.equals(closedFile)) {
					//TODO: maybe need to fix
					model.publishEvent(new FileSelectedEvent(this, gprPane.getField().getSgyFiles()));
				}
			}
			gprPane.fitFull();
		}

		if (closedFile instanceof CsvFile csvFile) {
			if (csvFile.equals(currentFile)) {
				currentFile = null;
			}
		}

		model.removeChart(closedFile);
		model.updateAuxElements();
		model.publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
		model.publishEvent(new WhatChanged(this, WhatChanged.Change.traceValues));
	}

	@EventListener
	private void fileOpened(FileOpenedEvent event) {

		List<File> openedFiles = event.getFiles();
		openedFiles.stream().flatMap(f -> f.isDirectory() ? Stream.of(f.listFiles()) : Stream.of(f)).forEach(file -> {
			System.out.println("ProfileView.fileOpened " + file.getAbsolutePath());
			model.getFileManager().getGprFiles().stream().filter(f -> f.getFile().equals(file)).findFirst().ifPresent(f -> {
				System.out.println("Loaded traces: " + f.getTraces().size());
				var gprPane = model.getProfileFieldByPattern(f);
				var vbox = (VBox) gprPane.getRootNode();

				//TODO:
				//gprPane.clear();
				//model.updateSgyFileOffsets();

				if (!model.getChartsContainer().getChildren().contains(vbox)) {
					model.getChartsContainer().getChildren().add(vbox);
					vbox.setPrefHeight(Math.max(400, vbox.getScene().getHeight()));
					vbox.setMinHeight(Math.max(400, vbox.getScene().getHeight() / 2));
				}

				gprPane.fitFull();

				fileSelected(new FileSelectedEvent(this, f));
				model.selectAndScrollToChart(gprPane);
			});
		});

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
		if (file instanceof GprFile gprFile) {
			var gprChart = model.getProfileField(gprFile);
			return gprChart != null
					? gprChart.getProfileScroll()
					: null;
		}
		if (file instanceof CsvFile csvFile) {
			var csvChart = model.getChart(csvFile);
			return csvChart.isPresent()
					? csvChart.get().getProfileScroll()
					: null;
		}
		return null;
	}

	@EventListener
	private void fileSelected(FileSelectedEvent event) {
		ProfileScroll profileScroll = getFileProfileScroll(event.getFile());
		setProfileScroll(profileScroll);

		if (event.getFile() != null) {
			currentFile = event.getFile();
			if (currentFile instanceof GprFile) {
				var gprChart = model.getProfileField(currentFile);

				ChangeListener<Number> sp2SizeListener = (observable, oldValue, newValue) -> {
					if (Math.abs(newValue.intValue() - oldValue.intValue()) > 1) {
						gprChart.setSize((int) (center.getWidth() - 21), (int) (Math.max(400, ((VBox) gprChart.getRootNode()).getHeight()) - 4));
					}
				};
				center.widthProperty().addListener(sp2SizeListener);
				//((VBox) gprChart.getRootNode()).widthProperty().addListener(sp2SizeListener);
				((VBox) gprChart.getRootNode()).heightProperty().addListener(sp2SizeListener);
			}
		}

		Platform.runLater(this::enableToolbar);
	}

	private Region getSpacer() {
		Region r3 = new Region();
		r3.setPrefWidth(7);
		return r3;
	}
}
