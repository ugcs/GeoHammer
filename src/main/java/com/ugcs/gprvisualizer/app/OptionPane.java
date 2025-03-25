package com.ugcs.gprvisualizer.app;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.ugcs.gprvisualizer.app.quality.AltitudeCheck;
import com.ugcs.gprvisualizer.app.quality.DataCheck;
import com.ugcs.gprvisualizer.app.quality.LineDistanceCheck;
import com.ugcs.gprvisualizer.app.quality.QualityCheck;
import com.ugcs.gprvisualizer.app.quality.QualityControl;
import com.ugcs.gprvisualizer.app.quality.QualityIssue;
import com.ugcs.gprvisualizer.draw.QualityLayer;
import com.ugcs.gprvisualizer.event.GriddingParamsSetted;
import com.ugcs.gprvisualizer.event.FileSelectedEvent;
import com.ugcs.gprvisualizer.event.WhatChanged;
import com.ugcs.gprvisualizer.math.LevelFilter;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.controlsfx.control.RangeSlider;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.github.thecoldwine.sigrun.common.ext.CsvFile;
import com.github.thecoldwine.sigrun.common.ext.PositionFile;
import com.github.thecoldwine.sigrun.common.ext.ResourceImageHolder;
import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.ugcs.gprvisualizer.app.commands.CommandRegistry;
import com.ugcs.gprvisualizer.gpr.Model;
import com.ugcs.gprvisualizer.gpr.PrefSettings;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.TabPane.TabClosingPolicy;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

@Component
public class OptionPane extends VBox implements InitializingBean {

	private static final double DEFAULT_SPACING = 5;

	private static final Insets DEFAULT_OPTIONS_INSETS = new Insets(10, 0, 10, 0);

	private static final int RIGHT_BOX_WIDTH = 350;

	private static final Logger log = LoggerFactory.getLogger(OptionPane.class);

	private final ExecutorService executor = Executors.newSingleThreadExecutor();

	@Autowired
	private MapView mapView;
	
	@Autowired
	private ApplicationEventPublisher eventPublisher;
	
	@Autowired
	private UiUtils uiUtils;

	@Autowired
	private ProfileView profileView;
	
	@Autowired
	private CommandRegistry commandRegistry;
	
	@Autowired
	private Model model;

	//@Autowired
	//private RadarMap radarMap;
	
	//@Autowired
	//private HoughScan houghScan;
	
	//@Autowired
	//private ExpHoughScan expHoughScan;

	@Autowired
	private LevelFilter levelFilter;

	@Autowired
	private PrefSettings prefSettings;
	
	private ToggleButton showGreenLineBtn = new ToggleButton("", 
			ResourceImageHolder.getImageView("level.png"));

	private final TabPane tabPane = new TabPane();

	private final Tab gprTab = new Tab("GPR");
	private final Tab csvTab = new Tab("CSV");

	private SgyFile selectedFile;

	private static final String BORDER_STYLING = """
		-fx-border-color: gray; 
		-fx-border-insets: 5;
		-fx-border-width: 1;
		-fx-border-style: solid;
		""";

	private ToggleButton  gridding = new ToggleButton("Gridding");
	private Map<String, TextField> filterInputs = new HashMap<>();
	private ProgressIndicator griddingProgressIndicator;
	private Button showGriddingButton;
	private Button showGriddingAllButton;

	public RangeSlider getGriddingRangeSlider() {
		return griddingRangeSlider;
	}

	private RangeSlider griddingRangeSlider;

	@Override
	public void afterPropertiesSet() throws Exception {

		this.setPadding(Insets.EMPTY);
		this.setPrefWidth(RIGHT_BOX_WIDTH);
		this.setMinWidth(0);
		this.setMaxWidth(RIGHT_BOX_WIDTH);

		prepareTabPane();
        this.getChildren().addAll(tabPane);
	}
	
	private void prepareTabPane() {

		tabPane.setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
        
        Tab tab2 = new Tab("Experimental");

		prepareCsvTab(csvTab);

        if (!AppContext.PRODUCTION) {
        	tabPane.getTabs().add(tab2);
        }
	}

	private void prepareCsvTab(Tab tab) {
		ToggleButton lowPassFilterButton = new ToggleButton("Low-pass filter");
		ToggleButton timeLagButton = new ToggleButton("GNSS time-lag");
		ToggleButton medianCorrection = new ToggleButton("Running median filter");
		ToggleButton qualityControl = new ToggleButton("Quality control");

		lowPassFilterButton.setMaxWidth(Double.MAX_VALUE);
		gridding.setMaxWidth(Double.MAX_VALUE);
		timeLagButton.setMaxWidth(Double.MAX_VALUE);
		medianCorrection.setMaxWidth(Double.MAX_VALUE);
		qualityControl.setMaxWidth(Double.MAX_VALUE);

		VBox t3 = new VBox();
		t3.setPadding(new Insets(10,8,10,8));
		t3.setSpacing(5);

		FilterActions lowPassActions = new FilterActions();
		lowPassActions.constraint = i -> {
			int value = Integer.parseInt(i);
			return value >= 0 && value < 10000;
		};
		lowPassActions.apply = i -> applyLowPassFilter(Integer.parseInt(i));
		lowPassActions.applyAll = i -> applyLowPassFilterToAll(Integer.parseInt(i));
		lowPassActions.undo = i -> applyLowPassFilter(0);
		StackPane lowPassOptions = createFilterOptions(
				Filter.lowpass,
				"Enter cutoff wavelength (fiducials)",
				lowPassActions);

		FilterActions timeLagActions = new FilterActions();
		timeLagActions.constraint = i -> {
			int value = Integer.parseInt(i);
			return Math.abs(value) < 10000;
		};
		timeLagActions.apply = i -> applyGnssTimeLag(Integer.parseInt(i));
		timeLagActions.applyAll = i -> applyGnssTimeLagToAll(Integer.parseInt(i));
		timeLagActions.undo = i -> applyGnssTimeLag(0);
		StackPane timeLagOptions = createFilterOptions(
				Filter.timelag,
				"Enter time-lag (fiducials)",
				timeLagActions);

		FilterActions medianCorrectionActions = new FilterActions();
		medianCorrectionActions.constraint = i -> {
			int value = Integer.parseInt(i);
			return value > 0;
		};
		medianCorrectionActions.apply = i -> applyMedianCorrection(Integer.parseInt(i));
		medianCorrectionActions.applyAll = i -> applyMedianCorrectionToAll(Integer.parseInt(i));
		StackPane medianCorrectionOptions = createFilterOptions(
				Filter.median_correction,
				"Enter window size",
				medianCorrectionActions);

		griddingProgressIndicator = new ProgressIndicator();
		griddingProgressIndicator.setVisible(false);
		griddingProgressIndicator.setManaged(false);
		VBox griddingOptions = createGriddingOptions(griddingProgressIndicator);
		StackPane griddingPane = new StackPane(griddingOptions, griddingProgressIndicator);

		qualityControl.addEventHandler(ActionEvent.ACTION, event ->
				toggleQualityLayer(qualityControl.isSelected()));
		QualityControlView qualityControlView = new QualityControlView(
				this::applyQualityControl,
				this::applyQualityControlToAll);

		t3.getChildren().addAll(List.of(lowPassFilterButton, lowPassOptions,
				gridding, griddingPane,
				timeLagButton, timeLagOptions,
				medianCorrection, medianCorrectionOptions,
				qualityControl, qualityControlView.getRoot()));

		lowPassFilterButton.setOnAction(getChangeVisibleAction(lowPassOptions));
		gridding.setOnAction(getChangeVisibleAction(griddingPane));
		timeLagButton.setOnAction(getChangeVisibleAction(timeLagOptions));
		medianCorrection.setOnAction(getChangeVisibleAction(medianCorrectionOptions));
		qualityControl.setOnAction(getChangeVisibleAction(qualityControlView.getRoot()));

		ScrollPane scrollPane = new ScrollPane(t3);
		scrollPane.setFitToWidth(true);
		scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
		// set reasonably large amount to fit tab height;
		// this seems the only way to force pane to fill container
		// in height
		scrollPane.setPrefHeight(10_000);

		tab.setContent(scrollPane);
	}

	private static @NotNull EventHandler<ActionEvent> getChangeVisibleAction(StackPane filterOptionsStackPane) {
		return e -> {
			filterOptionsStackPane.getChildren()
					.stream().filter(n -> n instanceof VBox).forEach(options -> {
						boolean visible = options.isVisible();
						options.setVisible(!visible);
						options.setManaged(!visible);
					});
		};
	}

	private void setGriddingMinMax() {
		if (!(selectedFile instanceof CsvFile)) {
			return;
		}

		double max = model.getChart((CsvFile) selectedFile).get().getSemanticMaxValue();
		double min = model.getChart((CsvFile) selectedFile).get().getSemanticMinValue();

		griddingRangeSlider.setMin(min);
		griddingRangeSlider.setMax(max);

		double width = max - min;
		if (width > 0.0) {
			griddingRangeSlider.setMajorTickUnit(width / 100);
			griddingRangeSlider.setMinorTickCount((int)(width / 1000));
			griddingRangeSlider.setBlockIncrement(width / 2000);
		}

		griddingRangeSlider.setLowValue(min);
		griddingRangeSlider.setHighValue(max);

		griddingRangeSlider.setDisable(false);
	}

	private enum Filter {
		lowpass,
		timelag,
		gridding_cellsize,
		gridding_blankingdistance,
		median_correction,
		quality_max_line_distance,
		quality_line_distance_tolerance,
		quality_max_altitude,
		quality_altitude_tolerance
	}

	private VBox createGriddingOptions(ProgressIndicator progressIndicator) {
		VBox griddingOptions = new VBox(5);
		griddingOptions.setPadding(new Insets(10, 0, 10, 0));

		VBox filterInput = new VBox(5);

		TextField gridCellSize = new TextField();
		gridCellSize.setPromptText("Enter cell size");
		filterInputs.put(Filter.gridding_cellsize.name(), gridCellSize);

		TextField gridBlankingDistance = new TextField();
		gridBlankingDistance.setPromptText("Enter blanking distance");
		filterInputs.put(Filter.gridding_blankingdistance.name(), gridBlankingDistance);

		showGriddingButton = new Button("Apply");
		showGriddingButton.setOnAction(e -> {
			prefSettings.saveSetting(Filter.gridding_cellsize.name(), Map.of(((CsvFile) selectedFile).getParser().getTemplate().getName(), gridCellSize.getText()));
			prefSettings.saveSetting(Filter.gridding_blankingdistance.name(), Map.of(((CsvFile) selectedFile).getParser().getTemplate().getName(), gridBlankingDistance.getText()));

			Platform.runLater(() -> setGriddingMinMax()); // min, max, min, max)); // minValue, maxValue));

			eventPublisher.publishEvent(new GriddingParamsSetted(this, Double.parseDouble(gridCellSize.getText()),
					Double.parseDouble(gridBlankingDistance.getText())));
			//griddingRangeSlider.setDisable(true);
		});
		showGriddingButton.setDisable(true);

		showGriddingAllButton = new Button("Apply to all");
		showGriddingAllButton.setOnAction(e -> {
			prefSettings.saveSetting(Filter.gridding_cellsize.name(), Map.of(((CsvFile) selectedFile).getParser().getTemplate().getName(), gridCellSize.getText()));
			prefSettings.saveSetting(Filter.gridding_blankingdistance.name(), Map.of(((CsvFile) selectedFile).getParser().getTemplate().getName(), gridBlankingDistance.getText()));

			//Platform.runLater(() -> setGriddingMinMax()); // min, max, min, max)); // minValue, maxValue));

			eventPublisher.publishEvent(new GriddingParamsSetted(this, Double.parseDouble(gridCellSize.getText()),
					Double.parseDouble(gridBlankingDistance.getText()), true));
		});
		showGriddingAllButton.setDisable(true);

		gridCellSize.textProperty().addListener((observable, oldValue, newValue) -> {
			try {
				if (newValue == null) {
					showGriddingButton.setDisable(true);
					showGriddingAllButton.setDisable(true);
					return;
				}
				double value = Double.parseDouble(newValue);
				boolean isValid = !newValue.isEmpty() && value > 0 && value < 100;
				showGriddingButton.setDisable(!isValid || gridBlankingDistance.getText().isEmpty());
				showGriddingAllButton.setDisable(!isValid || gridBlankingDistance.getText().isEmpty());
			} catch (NumberFormatException e) {
				showGriddingButton.setDisable(true);
				showGriddingAllButton.setDisable(true);
			}
		});

		gridBlankingDistance.textProperty().addListener((observable, oldValue, newValue) -> {
			try {
				if (newValue == null) {
					showGriddingButton.setDisable(true);
					showGriddingAllButton.setDisable(true);
					return;
				}
				double value = Double.parseDouble(newValue);
				boolean isValid = !newValue.isEmpty() && value > 0 && value < 100;
				showGriddingButton.setDisable(!isValid || gridCellSize.getText().isEmpty());
				showGriddingAllButton.setDisable(!isValid || gridCellSize.getText().isEmpty());
			} catch (NumberFormatException e) {
				showGriddingButton.setDisable(true);
				showGriddingAllButton.setDisable(true);
			}
		});

		Label label = new Label("Range");

		HBox coloursInput = new HBox(5);

		griddingRangeSlider = new RangeSlider();
		griddingRangeSlider.setShowTickLabels(true);
		griddingRangeSlider.setShowTickMarks(true);
		griddingRangeSlider.setLowValue(0);
		griddingRangeSlider.setHighValue(Double.MAX_VALUE);
		griddingRangeSlider.setDisable(true);
		griddingRangeSlider.setShowTickLabels(false);
		griddingRangeSlider.setShowTickMarks(false);

		Label minLabel = new Label("Min"); //+ griddingRangeSlider.getLowValue());
		Label maxLabel = new Label("Max"); //+ griddingRangeSlider.getHighValue());
		HBox center = new HBox(5);
		HBox.setHgrow(center, Priority.ALWAYS);
		coloursInput.getChildren().addAll(minLabel, center, maxLabel);

		griddingRangeSlider.lowValueProperty().addListener((obs, oldVal, newVal) -> {
				minLabel.setText("Min: " + newVal.intValue());//String.format("%.2f", newVal.doubleValue()));
				eventPublisher.publishEvent(new WhatChanged(this, WhatChanged.Change.griddingRange));
		});

		griddingRangeSlider.highValueProperty().addListener((obs, oldVal, newVal) -> {
			maxLabel.setText("Max: " + newVal.intValue());//String.format("%.2f", newVal.doubleValue()));
			eventPublisher.publishEvent(new WhatChanged(this, WhatChanged.Change.griddingRange));
		});

		VBox vbox = new VBox(10, griddingRangeSlider, coloursInput);

		filterInput.getChildren().addAll(gridCellSize, gridBlankingDistance, label, vbox);

		HBox filterButtons = new HBox(5);
		HBox rightBox = new HBox();
		HBox leftBox = new HBox(5);
		leftBox.getChildren().addAll(showGriddingButton);
		HBox.setHgrow(leftBox, Priority.ALWAYS);
		rightBox.getChildren().addAll(showGriddingAllButton);

		filterButtons.getChildren().addAll(leftBox, rightBox);

		griddingOptions.getChildren().addAll(filterInput, filterButtons);
		griddingOptions.setVisible(false);
		griddingOptions.setManaged(false);

		return griddingOptions;
	}

	public void griddingProgress(boolean inProgress) {
		Platform.runLater(() -> {
			showGriddingButton.setDisable(inProgress);
			showGriddingAllButton.setDisable(inProgress);
			griddingProgressIndicator.setVisible(inProgress);
			griddingProgressIndicator.setManaged(inProgress);
		});
	}

	static class FilterActions {
		Predicate<String> constraint = v -> true;
		Consumer<String> apply;
		Consumer<String> applyAll;
		Consumer<String> undo;

		boolean hasApply() {
			return apply != null;
		}

		boolean hasApplyAll() {
			return applyAll != null;
		}

		boolean hasUndo() {
			return undo != null;
		}
	}

	private @NotNull StackPane createFilterOptions(Filter filter, String prompt, FilterActions actions) {
		VBox filterOptions = new VBox(5);
		filterOptions.setPadding(new Insets(10, 0, 10, 0));

		ProgressIndicator progressIndicator = new ProgressIndicator();

		TextField filterInput = new TextField();
		filterInput.setPromptText(prompt);
		filterInputs.put(filter.name(), filterInput);

		Button applyButton = new Button("Apply");
		applyButton.setVisible(actions.hasApply());
		applyButton.setDisable(true);

		Button applyAllButton = new Button("Apply to all");
		applyAllButton.setVisible(actions.hasApplyAll());
		applyAllButton.setDisable(true);

		Button undoButton = new Button("Undo");
		undoButton.setVisible(actions.hasUndo());
		undoButton.setDisable(true);

		Runnable disableAndShowIndicator = () -> {
			progressIndicator.setVisible(true);
			progressIndicator.setManaged(true);

			filterInput.setDisable(true);
			applyButton.setDisable(true);
			undoButton.setDisable(true);
			applyAllButton.setDisable(true);
		};

		Runnable enableAndHideIndicator = () -> {
			filterInput.setDisable(false);
			applyButton.setDisable(false);
			undoButton.setDisable(false);
			applyAllButton.setDisable(false);

			progressIndicator.setVisible(false);
			progressIndicator.setManaged(false);
		};

		if (actions.hasApply()) {
			applyButton.setOnAction(event -> {
				disableAndShowIndicator.run();
				executor.submit(() -> {
					prefSettings.saveSetting(filter.name(), Map.of(
							((CsvFile) selectedFile).getParser().getTemplate().getName(),
							filterInput.getText()));
					try {
						actions.apply.accept(filterInput.getText());
					} catch (Exception e) {
						log.error("Error", e);
					} finally {
						Platform.runLater(enableAndHideIndicator);
					}
				});
			});
		}

		if (actions.hasApplyAll()) {
			applyAllButton.setOnAction(event -> {
				disableAndShowIndicator.run();
				executor.submit(() -> {
					prefSettings.saveSetting(filter.name(), Map.of(
							((CsvFile) selectedFile).getParser().getTemplate().getName(),
							filterInput.getText()));
					try {
						actions.applyAll.accept(filterInput.getText());
					} catch (Exception e) {
						log.error("Error", e);
					} finally {
						Platform.runLater(enableAndHideIndicator);
					}
				});
			});
		}

		if (actions.hasUndo()) {
			undoButton.setOnAction(e -> {
				actions.undo.accept(filterInput.getText());
				undoButton.setDisable(true);
			});
		}

		filterInput.textProperty().addListener((observable, oldValue, newValue) -> {
			boolean disable = true;
			try {
				if (newValue != null) {
					disable = newValue.isEmpty()
							|| (actions.constraint != null && !actions.constraint.test(newValue));
				}
			} catch (NumberFormatException e) {
				// keep disable = true
			}
			applyButton.setDisable(disable);
			applyAllButton.setDisable(disable);
		});

		HBox filterButtons = new HBox(5);
		HBox rightBox = new HBox();
		HBox leftBox = new HBox(5);
		leftBox.getChildren().addAll(applyButton, undoButton);
		HBox.setHgrow(leftBox, Priority.ALWAYS);
		rightBox.getChildren().addAll(applyAllButton);

		filterButtons.getChildren().addAll(leftBox, rightBox);

		filterOptions.getChildren().addAll(filterInput, filterButtons);
		filterOptions.setVisible(false);
		filterOptions.setManaged(false);

		progressIndicator.setVisible(false);
		progressIndicator.setManaged(false);
		return new StackPane(filterOptions, progressIndicator);
	}

	private void getNoImplementedDialog() {
		Dialog<String> dialog = new Dialog<>();
		dialog.setTitle("Not Implemented");
		dialog.setHeaderText("Feature Not Implemented");
		dialog.setContentText("This feature is not yet implemented.");
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK);
		dialog.showAndWait();
	}

	private void showQualityControl() {
		getNoImplementedDialog();
	}

	private void applyGnssTimeLag(int value) {
		var chart = model.getChart((CsvFile) selectedFile);
		chart.ifPresent(c -> c.gnssTimeLag(c.getSelectedSeriesName(), value));
		eventPublisher.publishEvent(new WhatChanged(this, WhatChanged.Change.csvDataFiltered));
	}

	private void applyGnssTimeLagToAll(int value) {
		var chart = model.getChart((CsvFile) selectedFile);
		chart.ifPresent(sc -> {
			String seriesName = sc.getSelectedSeriesName();
			model.getCharts().stream()
					.filter(c -> c.isSameTemplate((CsvFile) selectedFile))
					.forEach(c -> c.gnssTimeLag(seriesName, value));
		});
		eventPublisher.publishEvent(new WhatChanged(this, WhatChanged.Change.csvDataFiltered));
	}

	private void applyLowPassFilter(int value) {
		var chart = model.getChart((CsvFile) selectedFile);
		chart.ifPresent(c -> c.lowPassFilter(c.getSelectedSeriesName(), value));
		eventPublisher.publishEvent(new WhatChanged(this, WhatChanged.Change.csvDataFiltered));
	}

	private void applyLowPassFilterToAll(int value) {
		var chart = model.getChart((CsvFile) selectedFile);
		chart.ifPresent(sc -> {
			String seriesName = sc.getSelectedSeriesName();
			model.getCharts().stream()
					.filter(c -> c.isSameTemplate((CsvFile) selectedFile))
					.forEach(c -> c.lowPassFilter(seriesName, value));
		});
		eventPublisher.publishEvent(new WhatChanged(this, WhatChanged.Change.csvDataFiltered));
	}

	private void applyMedianCorrection(int value) {
		var chart = model.getChart((CsvFile) selectedFile);
		chart.ifPresent(c -> c.medianCorrection(c.getSelectedSeriesName(), value));
	}

	private void applyMedianCorrectionToAll(int value) {
		var chart = model.getChart((CsvFile) selectedFile);
		chart.ifPresent(sc -> {
			String seriesName = sc.getSelectedSeriesName();
			model.getCharts().stream()
					.filter(c -> c.isSameTemplate((CsvFile) selectedFile))
					.forEach(c -> c.medianCorrection(seriesName, value));
		});
	}	

	private void toggleQualityLayer(boolean active) {
		QualityLayer qualityLayer = mapView.getQualityLayer();
		if (qualityLayer.isActive() == active) {
			return;
		}
		qualityLayer.setActive(active);
		eventPublisher.publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
	}

	private List<QualityCheck> createQualityChecks(QualityControlParams params) {
		List<QualityCheck> checks = new ArrayList<>();
		if (params.maxLineDistance != null && params.lineDistanceTolerance != null) {
			checks.add(new LineDistanceCheck(
					params.maxLineDistance + params.lineDistanceTolerance
			));
		}
		double lineDistance = params.maxLineDistance != null
				? params.maxLineDistance
				: QualityControlView.DEFAULT_MAX_LINE_DISTANCE;
		checks.add(new DataCheck(
				0.35 * lineDistance
		));
		if (params.maxAltitude != null && params.altitudeTolerance != null) {
			checks.add(new AltitudeCheck(
					params.maxAltitude,
					params.altitudeTolerance,
					0.35 * lineDistance
			));
		}
		return checks;
	}

	private void applyQualityControl(QualityControlParams params) {
		if (selectedFile instanceof CsvFile csvFile) {
			QualityControl qualityControl = new QualityControl();
			List<CsvFile> files = List.of(csvFile);
			List<QualityCheck> checks = createQualityChecks(params);
			List<QualityIssue> issues = qualityControl.getQualityIssues(files, checks);

			mapView.getQualityLayer().setIssues(issues);
			eventPublisher.publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
		}
	}

	private void applyQualityControlToAll(QualityControlParams params) {
		QualityControl qualityControl = new QualityControl();
		List<CsvFile> files = model.getFileManager().getCsvFiles().stream()
				.filter(f -> f instanceof CsvFile)
				.map(f -> (CsvFile)f)
				.toList();
		List<QualityCheck> checks = createQualityChecks(params);
		List<QualityIssue> issues = qualityControl.getQualityIssues(files, checks);

		mapView.getQualityLayer().setIssues(issues);
		eventPublisher.publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
	}

	private Tab prepareGprTab(Tab tab1, SgyFile file) {
		VBox t1 = new VBox();

		//contrast
		t1.getChildren().addAll(profileView.getRight(file));
		// buttons
		t1.getChildren().addAll(levelFilter.getToolNodes());
		// map
		t1.getChildren().addAll(mapView.getRight(file));

		t1.getChildren().addAll(getPositionCsvButtons(file.getGroundProfileSource()));
        tab1.setContent(t1);
		return tab1;
	}

	private List<Node> getPositionCsvButtons(PositionFile positionFile) {
		VBox vBox = new VBox();
		vBox.setPadding(new Insets(10, 10, 10, 10));
		
		vBox.setStyle(BORDER_STYLING);
				
		vBox.getChildren().add(new Label("Elevation source: " + getSourceName(positionFile)));

		vBox.getChildren().addAll(levelFilter.getToolNodes2());

		return List.of(vBox);	
	}

	private String getSourceName(PositionFile positionFile) {
		return (positionFile != null && positionFile.getPositionFile() != null) ? positionFile.getPositionFile().getName() : "not found";
	}

	private ToggleButton prepareToggleButton(String title, 
			String imageName, MutableBoolean bool, Consumer<ToggleButton> consumer ) {
		
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
        selectedFile = event.getFile();

		if (selectedFile == null) {
			clear();
			return;
		}

        if (selectedFile instanceof CsvFile) {
            showTab(csvTab);
            setGriddingMinMax();
            setSavedFilterInputValue(Filter.lowpass);
            setSavedFilterInputValue(Filter.timelag);
            setSavedFilterInputValue(Filter.gridding_cellsize);
            setSavedFilterInputValue(Filter.gridding_blankingdistance);
        } else {
            showTab(gprTab);
            prepareGprTab(gprTab, selectedFile);
        }
    }

    //@EventListener(condition = "#event.isFileopened()")
	//@EventListener
    //private void fileOpened(FileOpenedEvent event) {
    //        clear();
    //}

	private void setSavedFilterInputValue(Filter filter) {
		var savedValue = prefSettings.getSetting(filter.name(), ((CsvFile) selectedFile).getParser().getTemplate().getName());
		filterInputs.get(filter.name()).setText(savedValue);
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

	public ToggleButton getGridding() {
		return gridding;
	}

	public void handleGriddingParamsSetted(double cellSize, double blankingDistance) {
		eventPublisher.publishEvent(new GriddingParamsSetted(this, cellSize, blankingDistance));
	}

	// quality control

	record QualityControlParams (
			Double maxLineDistance,
			Double lineDistanceTolerance,
			Double maxAltitude,
			Double altitudeTolerance
	) {}

	private class QualityControlView {

		public static double DEFAULT_MAX_LINE_DISTANCE = 1.0;
		public static double DEFAULT_LINE_DISTANCE_TOLERANCE = 0.2;
		public static double DEFAULT_MAX_ALTITUDE = 1.0;
		public static double DEFAULT_ALTITUDE_TOLERANCE = 0.2;

		private final StackPane root = new StackPane();
		private final ProgressIndicator progressIndicator = new ProgressIndicator();

		// input fields

		private final TextField maxLineDistance = new TextField(
				String.valueOf(DEFAULT_MAX_LINE_DISTANCE));
		private final TextField lineDistanceTolerance = new TextField(
				String.valueOf(DEFAULT_LINE_DISTANCE_TOLERANCE));
		private final TextField maxAltitude = new TextField(
				String.valueOf(DEFAULT_MAX_ALTITUDE));
		private final TextField altitudeTolerance = new TextField(
				String.valueOf(DEFAULT_ALTITUDE_TOLERANCE));

		// action buttons

		private final Button applyButton = new Button("Apply");
		private final Button applyToAllButton = new Button("Apply to all");

		public QualityControlView(
				Consumer<QualityControlParams> apply,
				Consumer<QualityControlParams> applyToAll) {
			progressIndicator.setVisible(false);
			progressIndicator.setManaged(false);

			// params
			Label lineDistanceLabel = new Label("Distance between lines");

			maxLineDistance.setPromptText("Distance between lines (m)");
			maxLineDistance.textProperty().addListener(this::inputChanged);
			filterInputs.put(Filter.quality_max_line_distance.name(), maxLineDistance);

			lineDistanceTolerance.setPromptText("Distance tolerance (m)");
			lineDistanceTolerance.textProperty().addListener(this::inputChanged);
			filterInputs.put(Filter.quality_line_distance_tolerance.name(), lineDistanceTolerance);

			Label altitudeLabel = new Label("Altitude AGL");

			maxAltitude.setPromptText("Altitude AGL (m)");
			maxAltitude.textProperty().addListener(this::inputChanged);
			filterInputs.put(Filter.quality_max_altitude.name(), maxAltitude);

			altitudeTolerance.setPromptText("Altitude tolerance (m)");
			altitudeTolerance.textProperty().addListener(this::inputChanged);
			filterInputs.put(Filter.quality_altitude_tolerance.name(), altitudeTolerance);

			VBox filterInput = new VBox(DEFAULT_SPACING,
					lineDistanceLabel,
					maxLineDistance,
					lineDistanceTolerance,
					altitudeLabel,
					maxAltitude,
					altitudeTolerance);

			// actions
			applyButton.setOnAction(event -> submitAction(apply));
			applyToAllButton.setOnAction(event -> submitAction(applyToAll));
			updateActionButtons();

			Region spacer = new Region();
			HBox.setHgrow(spacer, Priority.ALWAYS);
			HBox filterButtons = new HBox(DEFAULT_SPACING,
					applyButton,
					spacer,
					applyToAllButton);

			VBox filterOptions = new VBox(DEFAULT_SPACING, filterInput, filterButtons);
			filterOptions.setPadding(DEFAULT_OPTIONS_INSETS);
			filterOptions.setVisible(false);
			filterOptions.setManaged(false);

			root.getChildren().setAll(filterOptions, progressIndicator);
		}

		public StackPane getRoot() {
			return root;
		}

		public QualityControlParams getParams() {
			return new QualityControlParams(
					getTextAsDouble(maxLineDistance),
					getTextAsDouble(lineDistanceTolerance),
					getTextAsDouble(maxAltitude),
					getTextAsDouble(altitudeTolerance)
			);
		}

		private Double getTextAsDouble(TextField field) {
			if (field == null) {
				return null;
			}
			String text = field.getText();
			if (text == null || text.isEmpty()) {
				return null;
			}
			try {
				return Double.parseDouble(text);
			} catch (NumberFormatException e) {
				return null;
			}
		}

		private void inputChanged(ObservableValue<? extends String> observable, String oldValue, String newValue) {
			updateActionButtons();
		}

		private void submitAction(Consumer<QualityControlParams> action) {
			if (action == null) {
				return;
			}
			QualityControlParams params = getParams();
			disableAndShowIndicator();
			executor.submit(() -> {
				try {
					saveSettings();
					action.accept(params);
				} catch (Exception e) {
					log.error("Error", e);
				} finally {
					Platform.runLater(this::enableAndHideIndicator);
				}
			});
		}

		private void updateActionButtons() {
			// always on
			applyButton.setDisable(false);
			applyToAllButton.setDisable(false);
		}

		private void disableAndShowIndicator() {
			progressIndicator.setVisible(true);
			progressIndicator.setManaged(true);

			applyButton.setDisable(true);
			applyToAllButton.setDisable(true);

			maxLineDistance.setDisable(true);
			lineDistanceTolerance.setDisable(true);
			maxAltitude.setDisable(true);
			altitudeTolerance.setDisable(true);
		}

		private void enableAndHideIndicator() {
			maxLineDistance.setDisable(false);
			lineDistanceTolerance.setDisable(false);
			maxAltitude.setDisable(false);
			altitudeTolerance.setDisable(false);

			applyButton.setDisable(false);
			applyToAllButton.setDisable(false);

			progressIndicator.setVisible(false);
			progressIndicator.setManaged(false);
		}

		private void saveSettings() {
			if (selectedFile instanceof CsvFile csvFile) {
				String templateName = csvFile.getParser().getTemplate().getName();

				prefSettings.saveSetting(
						Filter.quality_max_line_distance.name(),
						Map.of(templateName, maxLineDistance.getText()));
				prefSettings.saveSetting(
						Filter.quality_line_distance_tolerance.name(),
						Map.of(templateName, lineDistanceTolerance.getText()));
				prefSettings.saveSetting(
						Filter.quality_max_altitude.name(),
						Map.of(templateName, maxAltitude.getText()));
				prefSettings.saveSetting(
						Filter.quality_altitude_tolerance.name(),
						Map.of(templateName, altitudeTolerance.getText()));
			}
		}
	}
}
