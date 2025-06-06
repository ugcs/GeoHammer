package com.ugcs.gprvisualizer.math;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.github.thecoldwine.sigrun.common.ext.TraceFile;
import com.ugcs.gprvisualizer.app.GPRChart;
import com.ugcs.gprvisualizer.app.commands.CancelKmlToFlag;
import com.ugcs.gprvisualizer.app.commands.KmlToFlag;

import com.ugcs.gprvisualizer.event.FileOpenedEvent;
import com.ugcs.gprvisualizer.event.FileSelectedEvent;
import com.ugcs.gprvisualizer.event.WhatChanged;
import com.ugcs.gprvisualizer.gpr.Settings;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.ugcs.gprvisualizer.app.AppContext;
import com.ugcs.gprvisualizer.app.UiUtils;
import com.ugcs.gprvisualizer.app.commands.BackgroundNoiseRemover;
import com.ugcs.gprvisualizer.app.commands.CommandRegistry;
import com.ugcs.gprvisualizer.app.commands.LevelClear;
import com.ugcs.gprvisualizer.app.commands.LevelGround;
import com.ugcs.gprvisualizer.app.commands.LevelManualSetter;
import com.ugcs.gprvisualizer.app.commands.LevelScanner;
import com.ugcs.gprvisualizer.app.commands.SpreadCoordinates;
import com.ugcs.gprvisualizer.draw.ToolProducer;
import com.ugcs.gprvisualizer.gpr.Model;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;

@Component
public class LevelFilter implements ToolProducer {

	@Autowired
	private Model model;	
	
	@Autowired
	private UiUtils uiUtils;

	@Autowired
	private CommandRegistry commandRegistry;

	private Button buttonRemoveLevel;

	private Button buttonLevelGround;

	private ToggleButton levelPreview;

	private Node slider;

	private Button buttonSpreadCoord;

	private Button buttonKmlToFlag;

	private Button buttonCancelKmlToFlag;

	private List<TraceFile> undoFiles;

	Settings levelSettings = new Settings();

	private TraceFile selectedFile;

	@EventListener
	private void selectFile(FileSelectedEvent event) {
		if (event.getFile() instanceof TraceFile traceFile) {
			selectedFile = traceFile;
		} else {
			selectedFile = null;
		}
		clearForNewFile(selectedFile);
	}

	@Override
	public List<Node> getToolNodes() {

		buttonSpreadCoord = commandRegistry.createButton(new SpreadCoordinates(), 
			e -> {
				updateButtons(selectedFile);
			});
			
		var buttons = List.of(commandRegistry.createButton(new BackgroundNoiseRemover()), buttonSpreadCoord);

		HBox hbox = new HBox();
		hbox.setSpacing(8);

		buttons.forEach(b -> {
			b.setMaxWidth(Double.MAX_VALUE);
		});

		HBox.setHgrow(buttons.get(0), Priority.ALWAYS);
		HBox.setHgrow(buttons.get(1), Priority.ALWAYS);

		hbox.getChildren().addAll(buttons);

		return List.of(hbox);
	}

	public List<Node> getToolNodes2() {
		buttonKmlToFlag = commandRegistry.createButton(new KmlToFlag(),
			e -> {
			});
		buttonCancelKmlToFlag = commandRegistry.createButton(new CancelKmlToFlag(),
			e -> {
			});

		levelPreview = uiUtils.prepareToggleButton("Level preview", null, 
				levelSettings.levelPreview,
				WhatChanged.Change.justdraw);

		if (buttonRemoveLevel == null) {	
			buttonRemoveLevel = commandRegistry.createButton(new LevelClear(this), e -> { 
				List<SgyFile> files = new ArrayList<>();
				files.addAll(model.getFileManager().getGprFiles());
				files.addAll(model.getFileManager().getCsvFiles());
				model.getFileManager().updateFiles(files);
				updateButtons(selectedFile);
			});
		}
		
		if (buttonLevelGround == null) {
			buttonLevelGround = commandRegistry.createButton(new LevelGround(this), e -> {				
				updateButtons(selectedFile);
			});		
		}		
		
		if (slider == null) {
			slider = uiUtils.createSlider(levelSettings.levelPreviewShift, WhatChanged.Change.justdraw, -50, 50, """
			Elevation lag, 
			traces""", 	new ChangeListener<Number>() {
				@Override
				public void changed(
					ObservableValue<? extends Number> observable,
					Number oldValue,
					Number newValue) {
					if (selectedFile == null) {
						return;
					}
					GPRChart gprChart = model.getGprChart(selectedFile);
					if (gprChart == null) {
						return;
					}

					TraceFile file = gprChart.getField().getFile();
					file.getGroundProfile().shift(newValue.intValue());
					model.publishEvent(new WhatChanged(this, WhatChanged.Change.traceValues));
				}
		});
		}

		List<Node> result = new ArrayList<>();
		
		if (!AppContext.PRODUCTION) {
		    result.addAll(Arrays.asList(
			 
			commandRegistry.createButton(new LevelScanner(), "scanLevel.png", 
					e -> {
						updateButtons(selectedFile);
					}),
			commandRegistry.createButton(new LevelManualSetter(model),
					e -> {
						updateButtons(selectedFile);
					}),
			
				buttonRemoveLevel, buttonLevelGround, levelPreview, slider, buttonKmlToFlag, buttonCancelKmlToFlag
		
		    	));		
		} else {
			HBox hbox = new HBox();
			hbox.setSpacing(8);

			buttonRemoveLevel.setMaxWidth(Double.MAX_VALUE);
			buttonLevelGround.setMaxWidth(Double.MAX_VALUE);

			HBox.setHgrow(buttonRemoveLevel, Priority.ALWAYS);
			HBox.setHgrow(buttonLevelGround, Priority.ALWAYS);

			hbox.getChildren().addAll(buttonLevelGround, buttonRemoveLevel);					
			result.addAll(List.of(
					slider,
					hbox 
			));
		}

		VBox vbox = new VBox();
		vbox.setSpacing(5);

		updateButtons(selectedFile);

		vbox.getChildren().addAll(result);

		return List.of(vbox);
	}

	private void updateButtons(TraceFile file) {

		if (buttonSpreadCoord != null) {
			buttonSpreadCoord.setDisable(!model.isSpreadCoordinatesNecessary());
		}

		if (buttonLevelGround != null) {
			buttonLevelGround.setDisable(!isGroundProfileExists(file));
		}
		if (buttonRemoveLevel != null) {
			buttonRemoveLevel.setDisable(!isUndoFilesExists());
		}
		if (levelPreview != null) {
			levelPreview.setDisable(!isGroundProfileExists(file));
		}
		if (slider != null) {
			slider.setDisable(!isGroundProfileExists(file));
		}
	}
	
	private boolean isUndoFilesExists() {
		return undoFiles != null && !undoFiles.isEmpty();
	}

	protected boolean isGroundProfileExists(TraceFile file) {
		GPRChart gprChart = model.getGprChart(file);
		return gprChart != null
				&& gprChart.getField().getFile().getGroundProfile() != null;
	}
	
	private void clearForNewFile(TraceFile file) {
		updateButtons(file);
	}
	
	@EventListener
	private void somethingChanged(WhatChanged changed) {
		if (changed.isUpdateButtons() || changed.isTraceCut()) {
			
			clearForNewFile(selectedFile);
			
			if (buttonSpreadCoord != null) {
				buttonSpreadCoord.setDisable(!model.isSpreadCoordinatesNecessary());
			}

			if (buttonKmlToFlag != null) {
				buttonKmlToFlag.setVisible(model.isKmlToFlagAvailable());
				buttonKmlToFlag.setManaged(model.isKmlToFlagAvailable());
			}

			if (buttonCancelKmlToFlag != null) {
				buttonCancelKmlToFlag.setVisible(model.isKmlToFlagAvailable());
				buttonCancelKmlToFlag.setManaged(model.isKmlToFlagAvailable());
			}
		}
	}

	@EventListener
	private void fileOpened(FileOpenedEvent event) {
		if (buttonSpreadCoord != null) {
			buttonSpreadCoord.setDisable(!model.isSpreadCoordinatesNecessary());
		}

		if (buttonKmlToFlag != null) {
			buttonKmlToFlag.setVisible(model.isKmlToFlagAvailable());
			buttonKmlToFlag.setManaged(model.isKmlToFlagAvailable());
		}

		if (buttonCancelKmlToFlag != null) {
			buttonCancelKmlToFlag.setVisible(model.isKmlToFlagAvailable());
			buttonCancelKmlToFlag.setManaged(model.isKmlToFlagAvailable());
		}
	}
	
	public List<TraceFile> getUndoFiles() {
		return undoFiles;
	}

	public void setUndoFiles(List<TraceFile> undoFiles) {
		this.undoFiles = undoFiles;
	}
}
