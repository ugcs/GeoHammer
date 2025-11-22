package com.ugcs.geohammer.chart.tool;

import java.util.*;
import java.util.function.Consumer;

import com.ugcs.geohammer.AppContext;
import com.ugcs.geohammer.chart.ScriptExecutionView;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.ProfileView;
import com.ugcs.geohammer.map.layer.radar.RadarMap;
import com.ugcs.geohammer.view.status.Status;
import com.ugcs.geohammer.service.script.ScriptExecutor;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
import com.ugcs.geohammer.chart.gpr.LevelFilter;
import javafx.application.Platform;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.StackPane;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.view.ResourceImageHolder;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.model.Model;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.control.TabPane.TabClosingPolicy;
import javafx.scene.layout.VBox;

@Component
public class OptionPane extends VBox implements InitializingBean {

    private static final Insets DEFAULT_GPR_OPTIONS_INSETS = new Insets(10, 8, 10, 8);

	private static final int RIGHT_BOX_WIDTH = 350;

	private ProfileView profileView;

	private Model model;

	private RadarMap radarMap;

	private LevelFilter levelFilter;

	private final Status status;

	private final TabPane tabPane = new TabPane();

	private final Tab gprTab = new Tab("GPR");
    private final Tab sonarTab = new Tab("Sonar");

	private SgyFile selectedFile;

	@Autowired
	private ScriptExecutor scriptExecutor;

    @Autowired
    private CsvTab csvTab;

    public OptionPane(ProfileView profileView, Model model,
					  RadarMap radarMap,
					  LevelFilter levelFilter,
					  Status status) {
		this.profileView = profileView;
		this.model = model;
		this.radarMap = radarMap;
		this.levelFilter = levelFilter;
		this.status = status;
	}

	@Override
	public void afterPropertiesSet() throws Exception {

		this.setPadding(Insets.EMPTY);
		this.setPrefWidth(RIGHT_BOX_WIDTH);
		this.setMinWidth(0);
		this.setMaxWidth(RIGHT_BOX_WIDTH);

        tabPane.setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
		this.getChildren().addAll(tabPane);
	}

	private void getNoImplementedDialog() {
		Dialog<String> dialog = new Dialog<>();
		dialog.setTitle("Not Implemented");
		dialog.setHeaderText("Feature Not Implemented");
		dialog.setContentText("This feature is not yet implemented.");
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK);
		dialog.initOwner(AppContext.stage);
		dialog.showAndWait();
	}

	private void showQualityControl() {
		getNoImplementedDialog();
	}

	private Tab prepareGprTab(Tab gprTab, TraceFile file) {

		// background
		StackPane backgroundOptions = createGprBackgroundOptions(file);
		ToggleButton backgroundToggle = new ToggleButton("Background");
		backgroundToggle.setMaxWidth(Double.MAX_VALUE);
		backgroundToggle.setOnAction(Tools.getChangeVisibleAction(backgroundOptions));

		// gridding
		StackPane griddingOptions = createGprGriddingOptions(file);
		ToggleButton griddingToggle = new ToggleButton("Gridding");
		griddingToggle.setMaxWidth(Double.MAX_VALUE);
		griddingToggle.setOnAction(Tools.getChangeVisibleAction(griddingOptions));

		// elevation
		StackPane elevationOptions = createGprElevationOptions(file);
		ToggleButton elevationToggle = new ToggleButton("Elevation");
		elevationToggle.setMaxWidth(Double.MAX_VALUE);
		elevationToggle.setOnAction(Tools.getChangeVisibleAction(elevationOptions));

        ScriptExecutionView scriptExecutionView = new ScriptExecutionView(model, status, selectedFile, scriptExecutor);
		StackPane scriptsPane = new StackPane(scriptExecutionView);
		ToggleButton scriptsButton = new ToggleButton("Scripts");
		scriptsButton.setMaxWidth(Double.MAX_VALUE);
		scriptsButton.setOnAction(Tools.getChangeVisibleAction(scriptsPane));

		VBox container = new VBox();
		container.setPadding(new Insets(10, 8, 10, 8));
		container.setSpacing(Tools.DEFAULT_SPACING);

		container.getChildren().addAll(
				backgroundToggle, backgroundOptions,
				griddingToggle, griddingOptions,
				elevationToggle, elevationOptions,
				scriptsButton, scriptsPane);

		ScrollPane scrollContainer = Tools.createVerticalScrollContainer(container);
		gprTab.setContent(scrollContainer);
		return gprTab;
	}

	private StackPane createGprBackgroundOptions(TraceFile file) {
		VBox options = new VBox(Tools.DEFAULT_SPACING);
		options.setPadding(DEFAULT_GPR_OPTIONS_INSETS);

		// contrast
		options.getChildren().addAll(profileView.getRight(file));
		// buttons: remove bg / spread coordinates
		options.getChildren().addAll(levelFilter.getToolNodes());

		options.setVisible(false);
		options.setManaged(false);

		return new StackPane(options);
	}

	private StackPane createGprGriddingOptions(TraceFile file) {
		VBox options = new VBox(Tools.DEFAULT_SPACING);
		options.setPadding(DEFAULT_GPR_OPTIONS_INSETS);

		// gpr gridding
		options.getChildren().addAll(radarMap.getControlNodes(file));

		options.setVisible(false);
		options.setManaged(false);

		return new StackPane(options);
	}

	private StackPane createGprElevationOptions(TraceFile file) {
		VBox options = new VBox(Tools.DEFAULT_SPACING);
		options.setPadding(DEFAULT_GPR_OPTIONS_INSETS);

		// elevation
		options.getChildren().addAll(levelFilter.getToolNodes2());

		options.setVisible(false);
		options.setManaged(false);

		return new StackPane(options);
	}

	private ToggleButton prepareToggleButton(String title,
											 String imageName, MutableBoolean bool, Consumer<ToggleButton> consumer) {

		ToggleButton btn = new ToggleButton(title,
				ResourceImageHolder.getImageView(imageName));

		btn.setSelected(bool.booleanValue());

		btn.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				bool.setValue(btn.isSelected());

				//eventPublisher.publishEvent(new WhatChanged(change));

				consumer.accept(btn);
			}
		});

		return btn;
	}

	@EventListener
    private void handleFileSelectedEvent(FileSelectedEvent event) {
		SgyFile previouslySelectedFile = selectedFile;
		selectedFile = event.getFile();
		if (selectedFile == null) {
			Platform.runLater(this::clear);
			return;
		}

        if (selectedFile instanceof CsvFile) {
            Platform.runLater(() -> showTab(csvTab));
        }

		if (selectedFile instanceof TraceFile traceFile) {
			// do nothing if selected file is same as previously selected
			if (!Objects.equals(event.getFile(), previouslySelectedFile)) {
				Platform.runLater(() -> {
					showTab(gprTab);
					prepareGprTab(gprTab, traceFile);
				});
			}
        }
    }

	private void clear() {
		tabPane.getTabs().clear();
	}

	private void showTab(Tab tab) {
        if (!tabPane.getTabs().contains(tab)) {
			clear();
            tabPane.getTabs().add(tab);
        }
        tabPane.getSelectionModel().select(tab);
    }
}
