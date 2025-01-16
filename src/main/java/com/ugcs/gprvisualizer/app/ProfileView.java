package com.ugcs.gprvisualizer.app;

import java.io.File;
import java.util.List;
import java.util.Optional;

import com.github.thecoldwine.sigrun.common.ext.CsvFile;
import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.ugcs.gprvisualizer.app.events.FileClosedEvent;
import com.ugcs.gprvisualizer.event.FileOpenedEvent;
import com.ugcs.gprvisualizer.event.FileSelectedEvent;
import com.ugcs.gprvisualizer.event.WhatChanged;
import javafx.event.ActionEvent;
import javafx.scene.layout.Pane;
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
	private final AuxElementEditHandler auxEditHandler;
	private final Navigator navigator;
	private final Saver saver;

	private final ToggleButton auxModeBtn = new ToggleButton("aux");

	private final ToolBar toolBar = new ToolBar();
	
	private final Button zoomInBtn = ResourceImageHolder.setButtonImage(ResourceImageHolder.ZOOM_IN, new Button());
	private final Button zoomOutBtn = ResourceImageHolder.setButtonImage(ResourceImageHolder.ZOOM_OUT, new Button());
	private final Button fitBtn = ResourceImageHolder.setButtonImage(ResourceImageHolder.FIT, new Button());

	private SgyFile currentFile;

	public ProfileView(Model model, AuxElementEditHandler auxEditHandler, Navigator navigator,
                       Saver saver) {
		this.model = model;
        this.auxEditHandler = auxEditHandler;
        this.navigator = navigator;
		this.saver = saver;
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

	private void prepareToolbar() {
		toolBar.setDisable(true);

		toolBar.getItems().addAll(saver.getToolNodes());
		toolBar.getItems().add(getSpacer());
		
		toolBar.getItems().addAll(auxEditHandler.getRightPanelTools());
		toolBar.getItems().add(getSpacer());

		toolBar.getItems().addAll(navigator.getToolNodes());
		toolBar.getItems().add(getSpacer());

		toolBar.getItems().add(zoomInBtn);
		toolBar.getItems().add(zoomOutBtn);
		toolBar.getItems().add(fitBtn);
		toolBar.getItems().add(getSpacer());
	}

	private VBox center;

	private Pane profileScrollPane;

	//center
	public Node getCenter() {
		if (center == null) {
			center = new VBox();
			center.setMinWidth(100);

			ScrollPane centerScrollPane = new ScrollPane();
			centerScrollPane.setStyle("-fx-focus-color: transparent; -fx-faint-focus-color: transparent;");

			profileScrollPane = new Pane();
			profileScrollPane.setMinHeight(ProfileScroll.HEIGHT);
			profileScrollPane.setPrefHeight(ProfileScroll.HEIGHT);
			profileScrollPane.setPrefWidth(center.getWidth());

			center.getChildren().addAll(toolBar, centerScrollPane);

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
		if (changed.isJustdraw() && currentFile != null
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
				if (currentFile.equals(closedFile)) {
					//TODO: maybe need to fix
					model.publishEvent(new FileSelectedEvent(this, gprPane.getField().getSgyFiles()));
				}
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
		for (File file : openedFiles) {
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

				fileSelected(new FileSelectedEvent(this, f));
				model.selectAndScrollToChart(gprPane);

			});
		}

		toolBar.setDisable(!model.isActive());
	}

	@EventListener
	private void fileSelected(FileSelectedEvent event) {
		if (event.getFile() != null && !event.getFile().equals(currentFile)) {
			currentFile = event.getFile();
			if (currentFile instanceof CsvFile csvFile) {

			} else {
				if (center.getChildren().get(1) instanceof ProfileScroll) {
					center.getChildren().remove(1);
				}

				var gprChart = model.getProfileField(currentFile);

				var profileScroll = gprChart.getProfileScroll();
				profileScroll.setVisible(true);
				center.getChildren().add(1, profileScroll);

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
	}

	private Region getSpacer() {
		Region r3 = new Region();
		r3.setPrefWidth(7);
		return r3;
	}
}
