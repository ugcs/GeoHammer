package com.ugcs.gprvisualizer.math;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.ugcs.gprvisualizer.app.commands.CancelKmlToFlag;
import com.ugcs.gprvisualizer.app.commands.KmlToFlag;

import com.ugcs.gprvisualizer.event.FileOpenedEvent;
import com.ugcs.gprvisualizer.event.FileSelectedEvent;
import com.ugcs.gprvisualizer.event.WhatChanged;
import com.ugcs.gprvisualizer.gpr.Settings;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

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

/**
 * 
 */
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

	//private boolean levelCalculated = true;

	private List<SgyFile> undoFiles;

	Settings levelSettings = new Settings();

	private SgyFile selectedFile;

	@EventListener
	private void selectFile(FileSelectedEvent event) {
		this.selectedFile = event.getFile();
		clearForNewFile(selectedFile);
	}

//	public void smoothLevel() {
//		for(SgyFile sf : model.getFileManager().getFiles()) {
//			
//			int result[] = new int[sf.getTraces().size()];
//			for(int i = 0; i < sf.getTraces().size(); i++) {
//				result[i] = avg(sf.getTraces(), i);				
//			}
//			
//			for(int i = 0; i < sf.getTraces().size(); i++) {
//				Trace tr = sf.getTraces().get(i);
//				tr.maxindex = result[i];				
//			}			
//		}
//	}

//	int R=8;
//	private int avg(List<Trace> traces, int i) {
//		
//		int from = i-R;
//		from = Math.max(0, from);
//		int to = i+R;
//		to = Math.min(to, traces.size()-1);
//		int sum = 0;
//		int cnt = 0;
//		for(int j=from; j<= to; j++) {
//			sum += traces.get(j).maxindex;
//			cnt++;
//		}
//		return sum/cnt;
//	}

	@Override
	public List<Node> getToolNodes() {

		buttonSpreadCoord = commandRegistry.createButton(new SpreadCoordinates(), 
			e -> {
				updateButtons(selectedFile);
				//buttonSpreadCoord.setVisible(false);
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
		//buttonLevelGround.setGraphic(ResourceImageHolder.getImageView("levelGrnd.png"));

		
		buttonKmlToFlag = commandRegistry.createButton(new KmlToFlag(),
			e -> {
				//buttonKmlToFlag.setVisible(false);
			});
		buttonCancelKmlToFlag = commandRegistry.createButton(new CancelKmlToFlag(),
			e -> {

			});

		levelPreview = uiUtils.prepareToggleButton("Level preview", null, 
				levelSettings.levelPreview,
				WhatChanged.Change.justdraw);

		if (buttonRemoveLevel == null) {	
			buttonRemoveLevel = commandRegistry.createButton(new LevelClear(this), e -> { 
				//levelCalculated = false; 
				List<SgyFile> files = model.getFileManager().getGprFiles();
				files.addAll(model.getFileManager().getCsvFiles());
				model.getFileManager().updateFiles(files);
				updateButtons(selectedFile);
			});
		}
		
		if (buttonLevelGround == null) {
			buttonLevelGround = commandRegistry.createButton(new LevelGround(this), e -> {				
				//levelCalculated = false;
				updateButtons(selectedFile);
			});		
		}		
		
		if (slider == null) {
			//slider = commandRegistry.createSlider(new ElevationLag(), e -> {
				//levelCalculated = false; 
			//	updateButtons(); 
			//});

			//model.getSettings().levelPreviewShift

			slider = uiUtils.createSlider(levelSettings.levelPreviewShift, WhatChanged.Change.justdraw, -50, 50, """
			Elevation lag, 
			traces""", 	new ChangeListener<Number>() {
				@Override
				public void changed(
					ObservableValue<? extends Number> observable, 
					Number oldValue,
					Number newValue) {
					SgyFile file = model.getProfileField(selectedFile).getField().getSgyFiles().getFirst();
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
						//levelCalculated = true; 
						updateButtons(selectedFile);
					}),
			commandRegistry.createButton(new LevelManualSetter(model),
					e -> {
						//levelCalculated = true; 
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
					//buttonRemoveLevel, 
					//buttonLevelGround, 
					//levelPreview, 
					slider,
					hbox 
					//buttonKmlToFlag, buttonCancelKmlToFlag
			));
		}

		VBox vbox = new VBox();
		vbox.setSpacing(5);

		updateButtons(selectedFile);

		//vbox.setDisable(!model.isActive());
		vbox.getChildren().addAll(result);

		return List.of(vbox);
	}

	private void updateButtons(SgyFile file) {

		if (buttonSpreadCoord != null) {
			buttonSpreadCoord.setDisable(!model.isSpreadCoordinatesNecessary());
			//buttonSpreadCoord.setManaged(model.isSpreadCoordinatesNecessary());
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

	protected boolean isGroundProfileExists(SgyFile file) {
		return model.getProfileField(file) != null &&
				model.getProfileField(file).getField().getSgyFiles().get(0).getGroundProfile() != null;
	}
	
	private void clearForNewFile(SgyFile file) {
		//levelCalculated = true; 
		updateButtons(file);
	}
	
	@EventListener
	private void somethingChanged(WhatChanged changed) {
		if (changed.isUpdateButtons() || changed.isTraceCut()) {
			
			clearForNewFile(selectedFile);
			
			if (buttonSpreadCoord != null) {
				buttonSpreadCoord.setDisable(!model.isSpreadCoordinatesNecessary());
				//buttonSpreadCoord.setManaged(model.isSpreadCoordinatesNecessary());
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
			//buttonSpreadCoord.setManaged(model.isSpreadCoordinatesNecessary());
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
	
	public List<SgyFile> getUndoFiles() {
		return undoFiles;
	}

	public void setUndoFiles(List<SgyFile> undoFiles) {
		this.undoFiles = undoFiles;
	}

}
