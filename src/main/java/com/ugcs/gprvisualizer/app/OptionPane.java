package com.ugcs.gprvisualizer.app;

import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.github.thecoldwine.sigrun.common.ext.TraceFile;
import com.ugcs.gprvisualizer.app.intf.Status;
import com.ugcs.gprvisualizer.app.quality.AltitudeCheck;
import com.ugcs.gprvisualizer.app.quality.DataCheck;
import com.ugcs.gprvisualizer.app.quality.LineDistanceCheck;
import com.ugcs.gprvisualizer.app.quality.QualityCheck;
import com.ugcs.gprvisualizer.app.quality.QualityControl;
import com.ugcs.gprvisualizer.app.quality.QualityIssue;
import com.ugcs.gprvisualizer.app.service.PythonScriptExecutorService;
import com.ugcs.gprvisualizer.app.yaml.Template;
import com.ugcs.gprvisualizer.draw.QualityLayer;
import com.ugcs.gprvisualizer.event.GriddingParamsSetted;
import com.ugcs.gprvisualizer.event.FileSelectedEvent;
import com.ugcs.gprvisualizer.event.WhatChanged;
import com.ugcs.gprvisualizer.math.LevelFilter;
import com.ugcs.gprvisualizer.utils.Check;
import com.ugcs.gprvisualizer.utils.Range;
import com.ugcs.gprvisualizer.utils.Views;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
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
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.github.thecoldwine.sigrun.common.ext.CsvFile;
import com.github.thecoldwine.sigrun.common.ext.ResourceImageHolder;
import com.github.thecoldwine.sigrun.common.ext.SgyFile;
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

import javax.annotation.Nullable;

@Component
public class OptionPane extends VBox implements InitializingBean {

	public static final double DEFAULT_SPACING = 5;

	public static final Insets DEFAULT_OPTIONS_INSETS = new Insets(10, 0, 10, 0);

	private static final Insets DEFAULT_GPR_OPTIONS_INSETS = new Insets(10, 8, 10, 8);

	private static final int RIGHT_BOX_WIDTH = 350;

	private static final float SLIDER_EXPAND_THRESHOLD = 0.15f;

	private static final float SLIDER_SHRINK_WIDTH_THRESHOLD = 0.3f;

	private static final Logger log = LoggerFactory.getLogger(OptionPane.class);

	private final ExecutorService executor = Executors.newSingleThreadExecutor();

	private MapView mapView;

	private ProfileView profileView;

	private Model model;

	private LevelFilter levelFilter;

	private PrefSettings prefSettings;

	private final Status status;

	private final Loader loader;

	private final SeriesSelectorView seriesSelectorView;

	private final Map<TemplateSeriesKey, GriddingRange> griddingRanges = new HashMap<>();

	// Warning label for grid input data changes
	private Label griddingWarningLabel;

	private final TabPane tabPane = new TabPane();

	private final Tab gprTab = new Tab("GPR");
	private final Tab csvTab = new Tab("CSV");

	private SgyFile selectedFile;

	private ToggleButton gridding = new ToggleButton("Gridding");
	private Map<String, TextField> filterInputs = new HashMap<>();
	private ProgressIndicator griddingProgressIndicator;
	private Button showGriddingButton;
	private Button showGriddingAllButton;
	private RangeSlider griddingRangeSlider;
	private StatisticsView statisticsView;
	@Nullable private ScriptExecutionView scriptExecutionView;

	@Autowired
	private PythonScriptExecutorService pythonScriptExecutorService;

	public OptionPane(MapView mapView, ProfileView profileView, Model model,
					  LevelFilter levelFilter, PrefSettings prefSettings,
					  Status status, Loader loader, SeriesSelectorView seriesSelectorView) {
		this.mapView = mapView;
		this.profileView = profileView;
		this.model = model;
		this.levelFilter = levelFilter;
		this.prefSettings = prefSettings;
		this.status = status;
		this.loader = loader;
		this.seriesSelectorView = seriesSelectorView;
	}

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

		prepareCsvTab(csvTab);
	}

	private void prepareCsvTab(Tab tab) {
		ToggleButton statisticsButton = new ToggleButton("Statistics");
		ToggleButton lowPassFilterButton = new ToggleButton("Low-pass filter");
		ToggleButton timeLagButton = new ToggleButton("GNSS time-lag");
		ToggleButton medianCorrection = new ToggleButton("Running median filter");
		ToggleButton qualityControl = new ToggleButton("Quality control");
		ToggleButton scriptsButton = new ToggleButton("Scripts");

		statisticsButton.setMaxWidth(Double.MAX_VALUE);
		lowPassFilterButton.setMaxWidth(Double.MAX_VALUE);
		gridding.setMaxWidth(Double.MAX_VALUE);
		timeLagButton.setMaxWidth(Double.MAX_VALUE);
		medianCorrection.setMaxWidth(Double.MAX_VALUE);
		qualityControl.setMaxWidth(Double.MAX_VALUE);
		scriptsButton.setMaxWidth(Double.MAX_VALUE);

		VBox container = new VBox();
		container.setPadding(new Insets(10, 8, 10, 8));
		container.setSpacing(5);

		statisticsView = new StatisticsView(model);
		StackPane statisticsPane = new StackPane(statisticsView);

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

		scriptExecutionView = new ScriptExecutionView(model, status, selectedFile, pythonScriptExecutorService);
		StackPane scriptsPane = new StackPane(scriptExecutionView);

		container.getChildren().addAll(List.of(
				statisticsButton, statisticsPane,
				lowPassFilterButton, lowPassOptions,
				gridding, griddingPane,
				timeLagButton, timeLagOptions,
				medianCorrection, medianCorrectionOptions,
				qualityControl, qualityControlView.getRoot(),
				scriptsButton, scriptsPane));

		statisticsButton.setOnAction(getChangeVisibleAction(statisticsPane));
		lowPassFilterButton.setOnAction(getChangeVisibleAction(lowPassOptions));
		gridding.setOnAction(getChangeVisibleAction(griddingPane));
		timeLagButton.setOnAction(getChangeVisibleAction(timeLagOptions));
		medianCorrection.setOnAction(getChangeVisibleAction(medianCorrectionOptions));
		qualityControl.setOnAction(getChangeVisibleAction(qualityControlView.getRoot()));
		scriptsButton.setOnAction(getChangeVisibleAction(scriptsPane));

		ScrollPane scrollContainer = createVerticalScrollContainer(container);
		scrollContainer.focusedProperty().addListener((observable, oldValue, newValue) -> {
			if (newValue) {
				// redirect focus to a tab pane
				Platform.runLater(() -> tab.getTabPane().requestFocus());
			}
		});

		StackPane seriesPane = new StackPane(seriesSelectorView);
		seriesPane.setPadding(new Insets(10, 16, 10, 16));
		seriesPane.setStyle("-fx-background-color: #666666;");

		VBox tabContainer = new VBox(seriesPane, scrollContainer);
		tab.setContent(tabContainer);
	}

	private static ScrollPane createVerticalScrollContainer(Node content) {
		ScrollPane scrollPane = new ScrollPane(content);
		scrollPane.setFitToWidth(true);
		scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
		// set reasonably large amount to fit tab height;
		// this seems the only way to force pane to fill container
		// in height
		scrollPane.setPrefHeight(10_000);
		return scrollPane;
	}

	private static @NonNull EventHandler<ActionEvent> getChangeVisibleAction(StackPane filterOptionsStackPane) {
		return e -> {
			filterOptionsStackPane.getChildren()
					.stream().filter(n -> n instanceof VBox).forEach(options -> {
						boolean visible = options.isVisible();
						options.setVisible(!visible);
						options.setManaged(!visible);
					});
		};
	}

	public GriddingRange getGriddingRange(CsvFile csvFile, String seriesName) {
		Check.notNull(csvFile);
		Check.notEmpty(seriesName);

		GriddingRange range = null;
		Template template = csvFile.getTemplate();
		if (template != null) {
			TemplateSeriesKey rangeKey = new TemplateSeriesKey(template.getName(), seriesName);
			range = griddingRanges.get(rangeKey);
		}
		return range != null
				? range
				: getGriddingRangeSliderValues();
	}

	@EventListener
	public void on(WhatChanged changed) {
		if (changed.isTraceCut()) {
			showGridInputDataChangedWarning(true);
		}
		if (changed.isCsvDataZoom() || changed.isTraceCut() || changed.isTraceSelected()) {
			if (statisticsView != null) {
				Platform.runLater(() -> {
					statisticsView.update(selectedFile);
				});
			}
		}
	}

	public record TemplateSeriesKey(String templateName, String seriesName) {
	}

	public record GriddingRange(double lowValue, double highValue, double min, double max) {
	}

	private @Nullable Range getSelectedSeriesRange() {
		if (selectedFile instanceof CsvFile csvFile) {
			double min = Double.POSITIVE_INFINITY;
			double max = Double.NEGATIVE_INFINITY;

			for (SensorLineChart chart : model.getCsvCharts()) {
				if (csvFile.isSameTemplate(chart.getFile())) {
					min = Math.min(min, chart.getSemanticMinValue());
					max = Math.max(max, chart.getSemanticMaxValue());
				}
			}
			return Double.isInfinite(min) || Double.isInfinite(max)
					? null
					: new Range(min, max);
		}
		return null;
	}

	private void updateGriddingMinMaxPreserveUserRange() {
		updateGriddingMinMaxPreserveUserRange(null);
	}

	/**
	 * Updates the gridding range slider min/max values if needed, while maintaining
	 * the user's selected range proportionally.
	 */
	private void updateGriddingMinMaxPreserveUserRange(Range rangeBefore) {
		if (!(selectedFile instanceof CsvFile)) {
			return;
		}

		// get current min/max range
		Range range = getSelectedSeriesRange();
		if (range == null) {
			return;
		}
		if (range.getWidth() == 0) {
			range = range.scaleToWidth(1, 0.5);
		}

		GriddingRange defaultGriddingRange = new GriddingRange(
				range.getMin().doubleValue(),
				range.getMax().doubleValue(),
				range.getMin().doubleValue(),
				range.getMax().doubleValue());
		GriddingRange griddingRange = loadGriddingRange().orElse(defaultGriddingRange);

		// Only update min/max if they changed
		if (rangeBefore != null
				&& (!Objects.equals(rangeBefore.getMin(), range.getMin())
				|| !Objects.equals(rangeBefore.getMax(), range.getMax()))) {

			// Calculate relative positions within the range
			double widthBefore = rangeBefore.getWidth();
			double lowRatio = widthBefore > 0
					? (griddingRange.lowValue - rangeBefore.getMin().doubleValue()) / widthBefore
					: 0;
			lowRatio = Math.clamp(lowRatio, 0, 1);
			double highRatio = widthBefore > 0
					? (griddingRange.highValue - rangeBefore.getMin().doubleValue()) / widthBefore
					: 1;
			highRatio = Math.clamp(highRatio, 0, 1);

			// maintain the relative range of user's selection
			griddingRange = new GriddingRange(
					range.getMin().doubleValue() + lowRatio * range.getWidth(),
					range.getMin().doubleValue() + highRatio * range.getWidth(),
					range.getMin().doubleValue(),
					range.getMax().doubleValue());
		}

		updateGriddingRangeSlider(griddingRange);
		griddingRangeSlider.setDisable(false);
	}

	private void updateGriddingRangeSlider(GriddingRange sliderRange) {
		griddingRangeSlider.setMax(sliderRange.max);
		griddingRangeSlider.setMin(sliderRange.min);

		// assign and adjust values
		if (sliderRange.highValue > griddingRangeSlider.getLowValue()) {
			griddingRangeSlider.adjustHighValue(sliderRange.highValue);
			griddingRangeSlider.adjustLowValue(sliderRange.lowValue);
		} else {
			griddingRangeSlider.adjustLowValue(sliderRange.lowValue);
			griddingRangeSlider.adjustHighValue(sliderRange.highValue);
		}

		expandGriddingRangeSlider();
		saveGriddingRange();
	}

	private void expandGriddingRangeSlider() {
		double min = griddingRangeSlider.getMin();
		double max = griddingRangeSlider.getMax();

		double l = griddingRangeSlider.getLowValue();
		double r = griddingRangeSlider.getHighValue();

		// shrink
		if ((r - l) > 1e-12 && (r - l) / (max - min) < SLIDER_SHRINK_WIDTH_THRESHOLD) {
			double center = l + 0.5 * (r - l);
			double centerRatio = (center - min) / (max - min);
			double newWidth = (r - l) / SLIDER_SHRINK_WIDTH_THRESHOLD;

			min = center - centerRatio * newWidth;
			max = center + (1.0 - centerRatio) * newWidth;
		}

		// expand
		double threshold = SLIDER_EXPAND_THRESHOLD * (max - min);
		double k = SLIDER_EXPAND_THRESHOLD; // expand margin ratio to a new width
		if (l - min < threshold) {
			// l - min2 = margin
			// margin = k * (max2 - min2)
			// (r - min2) / (max2 - min2) = (r - min) / (max - min)
			double rRatio = (r - min) / (max - min);
			min = (k * r - rRatio * l) / (k - rRatio);
			max = min + (r - min) / rRatio;
		}
		if (max - r < threshold) {
			// max2 - r = margin
			// margin = k * (max2 - min2)
			// (l - min2) / (max2 - min2) = (l - min) / (max - min)
			double lRatio = (l - min) / (max - min);
			min = (r * lRatio + l * (k - 1)) / (lRatio + k - 1);
			max = (r - k * min) / (1 - k);
		}

		griddingRangeSlider.setMin(min);
		griddingRangeSlider.setMax(max);

		updateGriddingRangeSliderTicks();
	}

	private void updateGriddingRangeSliderTicks() {
		double width = griddingRangeSlider.getMax() - griddingRangeSlider.getMin();
		if (width > 0.0) {
			griddingRangeSlider.setMajorTickUnit(width / 100);
			griddingRangeSlider.setMinorTickCount((int) (width / 1000));
			griddingRangeSlider.setBlockIncrement(width / 2000);
		}
	}

	private @NonNull GriddingRange getGriddingRangeSliderValues() {
		return new GriddingRange(griddingRangeSlider.getLowValue(),
				griddingRangeSlider.getHighValue(),
				griddingRangeSlider.getMin(),
				griddingRangeSlider.getMax());
	}

	private void saveGriddingRange() {
		if (!(selectedFile instanceof CsvFile csvFile)) {
			return;
		}
		Template template = csvFile.getTemplate();
		if (template == null) {
			return;
		}
		SensorLineChart chart = model.getCsvChart(csvFile).orElse(null);
		if (chart == null) {
			return;
		}
		TemplateSeriesKey rangeKey = new TemplateSeriesKey(
				template.getName(),
				chart.getSelectedSeriesName());
		GriddingRange griddingRange = getGriddingRangeSliderValues();
		griddingRanges.put(rangeKey, griddingRange);
	}

	private Optional<GriddingRange> loadGriddingRange() {
		if (!(selectedFile instanceof CsvFile csvFile)) {
			return Optional.empty();
		}
		Template template = csvFile.getTemplate();
		if (template == null) {
			return Optional.empty();
		}
		SensorLineChart chart = model.getCsvChart(csvFile).orElse(null);
		if (chart == null) {
			return Optional.empty();
		}

		TemplateSeriesKey rangeKey = new TemplateSeriesKey(
				template.getName(),
				chart.getSelectedSeriesName());
		return Optional.ofNullable(griddingRanges.get(rangeKey));
	}

	private void onResetGriddingRange(ActionEvent event) {
		// get current min/max range
		Range range = getSelectedSeriesRange();
		if (range == null) {
			return;
		}
		if (range.getWidth() == 0) {
			range = range.scaleToWidth(1, 0.5);
		}
		GriddingRange griddingRange = new GriddingRange(
				range.getMin().doubleValue(),
				range.getMax().doubleValue(),
				range.getMin().doubleValue(),
				range.getMax().doubleValue());
		updateGriddingRangeSlider(griddingRange);
	}

	private enum Filter {
		lowpass,
		timelag,
		gridding_cellsize,
		gridding_blankingdistance,
		gridding_analytic_signal_enabled,
		gridding_hillshading_enabled,
		gridding_hillshading_azimuth,
		gridding_hillshading_altitude,
		gridding_hillshading_intensity,
		median_correction,
		quality_max_line_distance,
		quality_line_distance_tolerance,
		quality_max_altitude,
		quality_altitude_tolerance,
		gridding_smoothing_enabled;
	}

	/**
	 * Shows or hides the warning about grid input data changes.
	 *
	 * @param show true to show the warning, false to hide it
	 */
	private void showGridInputDataChangedWarning(boolean show) {
		if (griddingWarningLabel != null) {
			Platform.runLater(() -> {
				griddingWarningLabel.setVisible(show);
				griddingWarningLabel.setManaged(show);
			});
		}
	}

	private VBox createGriddingOptions(ProgressIndicator progressIndicator) {
		VBox griddingOptions = new VBox(5);
		griddingOptions.setPadding(new Insets(10, 0, 10, 0));

		// Create warning label
		griddingWarningLabel = new Label("Warning: Grid needs to be recalculated.");
		griddingWarningLabel.setStyle("-fx-text-fill: #E7AE3CFF; -fx-font-weight: bold;");
		griddingWarningLabel.setVisible(false);
		griddingWarningLabel.setManaged(false);
		griddingOptions.getChildren().add(griddingWarningLabel);

		VBox filterInput = new VBox(5);

		TextField gridCellSize = new TextField();
		gridCellSize.setPromptText("Enter cell size");
		filterInputs.put(Filter.gridding_cellsize.name(), gridCellSize);

		TextField gridBlankingDistance = new TextField();
		gridBlankingDistance.setPromptText("Enter blanking distance");
		filterInputs.put(Filter.gridding_blankingdistance.name(), gridBlankingDistance);

		// Analytic signal controls
		CheckBox analyticSignalEnabled = new CheckBox("Analytic signal");
		BooleanProperty analyticSignalBoolProperty = new SimpleBooleanProperty(false);
		analyticSignalEnabled.selectedProperty().bindBidirectional(analyticSignalBoolProperty);
		TextField analyticSignalEnabledText = new TextField("false");
		analyticSignalEnabledText.textProperty().addListener((observable, oldValue, newValue) -> {
			analyticSignalBoolProperty.set(Boolean.parseBoolean(newValue));
		});

		filterInputs.put(Filter.gridding_analytic_signal_enabled.name(), analyticSignalEnabledText);
		analyticSignalEnabled.selectedProperty().addListener((obs, oldVal, newVal) -> {
			filterInputs.get(Filter.gridding_analytic_signal_enabled.name()).setText(newVal.toString());
			String templateName = ((CsvFile) selectedFile).getParser().getTemplate().getName();
			prefSettings.saveSetting(Filter.gridding_analytic_signal_enabled.name(), templateName, newVal.toString());

			// Publish GriddingParamsSetted event with current parameters
			publishGriddingParameters(this, false);
		});

		// Hill-shading controls
		//Label hillShadingLabel = new Label("Hill-shading");
		//hillShadingLabel.setStyle("-fx-font-weight: bold;");

		// Checkbox for enabling/disabling hill-shading
		CheckBox hillShadingEnabled = new CheckBox("Enable hill-shading");
		BooleanProperty hillShadingBoolProperty = new SimpleBooleanProperty(false);
		hillShadingEnabled.selectedProperty().bindBidirectional(hillShadingBoolProperty);
		TextField hillShadingEnabledText = new TextField("false");
		hillShadingEnabledText.textProperty().addListener((observable, oldValue, newValue) -> {
			hillShadingBoolProperty.set(Boolean.parseBoolean(newValue));
		});

		filterInputs.put(Filter.gridding_hillshading_enabled.name(), hillShadingEnabledText);
		hillShadingEnabled.selectedProperty().addListener((obs, oldVal, newVal) -> {
			filterInputs.get(Filter.gridding_hillshading_enabled.name()).setText(newVal.toString());
			String templateName = ((CsvFile) selectedFile).getParser().getTemplate().getName();
			prefSettings.saveSetting(Filter.gridding_hillshading_enabled.name(), templateName, newVal.toString());

			// Publish GriddingParamsSetted event with current parameters
			publishGriddingParameters(this, false);
		});

		// Checkbox for enabling/disabling hill-shading
		CheckBox smoothingEnabled = new CheckBox("Enable smoothing");
		BooleanProperty smoothingBoolProperty = new SimpleBooleanProperty(false);
		smoothingEnabled.selectedProperty().bindBidirectional(smoothingBoolProperty);
		TextField smoothingEnabledText = new TextField("false");
		smoothingEnabledText.textProperty().addListener((observable, oldValue, newValue) -> {
			smoothingBoolProperty.set(Boolean.parseBoolean(newValue));
		});

		filterInputs.put(Filter.gridding_smoothing_enabled.name(), smoothingEnabledText);
		smoothingEnabled.selectedProperty().addListener((obs, oldVal, newVal) -> {
			filterInputs.get(Filter.gridding_smoothing_enabled.name()).setText(newVal.toString());
			String templateName = ((CsvFile) selectedFile).getParser().getTemplate().getName();
			prefSettings.saveSetting(Filter.gridding_smoothing_enabled.name(), templateName, newVal.toString());

			// Publish GriddingParamsSetted event with current parameters
			publishGriddingParameters(this, false);
		});

		// Azimuth slider
		Label azimuthLabel = new Label("Light direction (azimuth): 180°");
		javafx.scene.control.Slider azimuthSlider = new javafx.scene.control.Slider(0, 360, 180);
		azimuthSlider.setShowTickLabels(true);
		azimuthSlider.setShowTickMarks(true);
		azimuthSlider.setMajorTickUnit(90);
		azimuthSlider.setMinorTickCount(3);
		azimuthSlider.setSnapToTicks(false);
		filterInputs.put(Filter.gridding_hillshading_azimuth.name(), new TextField("180.0"));
		azimuthSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
			azimuthLabel.setText(String.format("Light direction (azimuth): %.0f°", newVal.doubleValue()));
			filterInputs.get(Filter.gridding_hillshading_azimuth.name()).setText(newVal.toString());
			String templateName = ((CsvFile) selectedFile).getParser().getTemplate().getName();
			prefSettings.saveSetting(Filter.gridding_hillshading_azimuth.name(), templateName, newVal.toString());

			// Publish GriddingParamsSetted event with current parameters
			publishGriddingParameters(this, false);
		});

		// Altitude slider
		Label altitudeLabel = new Label("Light height (altitude): 45°");
		javafx.scene.control.Slider altitudeSlider = new javafx.scene.control.Slider(0, 90, 45);
		altitudeSlider.setShowTickLabels(true);
		altitudeSlider.setShowTickMarks(true);
		altitudeSlider.setMajorTickUnit(15);
		altitudeSlider.setMinorTickCount(2);
		altitudeSlider.setSnapToTicks(false);
		filterInputs.put(Filter.gridding_hillshading_altitude.name(), new TextField("45.0"));
		altitudeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
			altitudeLabel.setText(String.format("Light height (altitude): %.0f°", newVal.doubleValue()));
			filterInputs.get(Filter.gridding_hillshading_altitude.name()).setText(newVal.toString());
			String templateName = ((CsvFile) selectedFile).getParser().getTemplate().getName();
			prefSettings.saveSetting(Filter.gridding_hillshading_altitude.name(), templateName, newVal.toString());

			// Publish GriddingParamsSetted event with current parameters
			publishGriddingParameters(this, false);
		});

		// Intensity slider
		Label intensityLabel = new Label("Effect intensity: 0.5");
		javafx.scene.control.Slider intensitySlider = new javafx.scene.control.Slider(0, 1, 0.5);
		intensitySlider.setShowTickLabels(true);
		intensitySlider.setShowTickMarks(true);
		intensitySlider.setMajorTickUnit(0.25);
		intensitySlider.setMinorTickCount(1);
		intensitySlider.setSnapToTicks(false);
		filterInputs.put(Filter.gridding_hillshading_intensity.name(), new TextField("0.5"));
		intensitySlider.valueProperty().addListener((obs, oldVal, newVal) -> {
			intensityLabel.setText(String.format("Effect intensity: %.2f", newVal.doubleValue()));
			filterInputs.get(Filter.gridding_hillshading_intensity.name()).setText(newVal.toString());
			String templateName = ((CsvFile) selectedFile).getParser().getTemplate().getName();
			prefSettings.saveSetting(Filter.gridding_hillshading_intensity.name(), templateName, newVal.toString());

			// Publish GriddingParamsSetted event with current parameters
			publishGriddingParameters(this, false);
		});

		// Hill-shading controls container
		VBox postProcessingOptions = new VBox(5,
			//hillShadingLabel,
				hillShadingEnabled,
				smoothingEnabled,
				analyticSignalEnabled
			//azimuthLabel, azimuthSlider,
			//altitudeLabel, altitudeSlider,
			//intensityLabel, intensitySlider
		);
		postProcessingOptions.setPadding(new Insets(5, 0, 5, 0));
		postProcessingOptions.setStyle("-fx-border-color: lightgray; -fx-border-width: 1; -fx-border-radius: 5; -fx-padding: 5;");

		showGriddingButton = new Button("Apply");
		showGriddingButton.setOnAction(e -> {
			String templateName = ((CsvFile) selectedFile).getParser().getTemplate().getName();
			prefSettings.saveSetting(Filter.gridding_cellsize.name(), templateName, gridCellSize.getText());
			prefSettings.saveSetting(Filter.gridding_blankingdistance.name(), templateName, gridBlankingDistance.getText());
//			prefSettings.saveSetting(Filter.gridding_hillshading_enabled.name(), templateName, filterInputs.get(Filter.gridding_hillshading_enabled.name()).getText());
//			prefSettings.saveSetting(Filter.gridding_hillshading_azimuth.name(), templateName, filterInputs.get(Filter.gridding_hillshading_azimuth.name()).getText());
//			prefSettings.saveSetting(Filter.gridding_hillshading_altitude.name(), templateName, filterInputs.get(Filter.gridding_hillshading_altitude.name()).getText());
//			prefSettings.saveSetting(Filter.gridding_hillshading_intensity.name(), templateName, filterInputs.get(Filter.gridding_hillshading_intensity.name()).getText());

			publishGriddingParameters(showGriddingButton, false);
			showGridInputDataChangedWarning(false);
		});
		showGriddingButton.setDisable(true);

		showGriddingAllButton = new Button("Apply to all");
		showGriddingAllButton.setOnAction(e -> {
			String templateName = ((CsvFile) selectedFile).getParser().getTemplate().getName();
			prefSettings.saveSetting(Filter.gridding_cellsize.name(), templateName, gridCellSize.getText());
			prefSettings.saveSetting(Filter.gridding_blankingdistance.name(), templateName, gridBlankingDistance.getText());
			//prefSettings.saveSetting(Filter.gridding_hillshading_enabled.name(), Map.of(templateName, filterInputs.get(Filter.gridding_hillshading_enabled.name()).getText()));
			//prefSettings.saveSetting(Filter.gridding_hillshading_azimuth.name(), Map.of(templateName, filterInputs.get(Filter.gridding_hillshading_azimuth.name()).getText()));
			//prefSettings.saveSetting(Filter.gridding_hillshading_altitude.name(), Map.of(templateName, filterInputs.get(Filter.gridding_hillshading_altitude.name()).getText()));
			//prefSettings.saveSetting(Filter.gridding_hillshading_intensity.name(), Map.of(templateName, filterInputs.get(Filter.gridding_hillshading_intensity.name()).getText()));

			publishGriddingParameters(showGriddingAllButton, true);
			showGridInputDataChangedWarning(false);
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
				boolean blankingDistanceEmpty = gridBlankingDistance == null || gridBlankingDistance.getText().isEmpty();
				showGriddingButton.setDisable(!isValid || blankingDistanceEmpty);
				showGriddingAllButton.setDisable(!isValid || blankingDistanceEmpty);
				showGridInputDataChangedWarning(true);
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
				showGridInputDataChangedWarning(true);
			} catch (NumberFormatException e) {
				showGriddingButton.setDisable(true);
				showGriddingAllButton.setDisable(true);
			}
		});

		HBox labelAndReset = new HBox(10);
		Label label = new Label("Range");
		Button resetButton = Views.createGlyphButton("↺", 16, 16);
		resetButton.setOnAction(this::onResetGriddingRange);

		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		labelAndReset.getChildren().addAll(label, spacer, resetButton);

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
			setFormattedValue(newVal, "Min: ", minLabel);
			saveGriddingRange();
			model.publishEvent(new WhatChanged(this, WhatChanged.Change.griddingRange));
		});

		griddingRangeSlider.highValueProperty().addListener((obs, oldVal, newVal) -> {
			setFormattedValue(newVal, "Max: ", maxLabel);
			saveGriddingRange();
			model.publishEvent(new WhatChanged(this, WhatChanged.Change.griddingRange));
		});

		griddingRangeSlider.setOnMouseReleased(event -> {
			expandGriddingRangeSlider();
			saveGriddingRange();
		});

		VBox vbox = new VBox(10, griddingRangeSlider, coloursInput);

		filterInput.getChildren().addAll(gridCellSize, gridBlankingDistance, labelAndReset, vbox, postProcessingOptions);

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

	private void setFormattedValue(Number newVal, String prefix, Label label) {
		double range = griddingRangeSlider.getMax() - griddingRangeSlider.getMin();
		String valueText;
		if (range < 10) {
			valueText = String.format(prefix + "%.2f", newVal.doubleValue());
		} else if (range < 100) {
			valueText = String.format(prefix + "%.1f", newVal.doubleValue());
		} else {
			valueText = prefix + newVal.intValue();
		}
		label.setText(valueText);
	}

	public void griddingProgress(boolean inProgress) {
		Platform.runLater(() -> {
			showGriddingButton.setDisable(inProgress);
			showGriddingAllButton.setDisable(inProgress);
			griddingProgressIndicator.setVisible(inProgress);
			griddingProgressIndicator.setManaged(inProgress);
		});
	}

	private void publishGriddingParameters(Object source, boolean toAll) {
		model.publishEvent(new GriddingParamsSetted(
				source,
				Double.parseDouble(filterInputs.get(Filter.gridding_cellsize.name()).getText()),
				Double.parseDouble(filterInputs.get(Filter.gridding_blankingdistance.name()).getText()),
				toAll,
				Double.parseDouble(filterInputs.get(Filter.gridding_cellsize.name()).getText()) > GriddingParamsSetted.IDW_CELL_SIZE_THRESHOLD
						? GriddingParamsSetted.InterpolationMethod.IDW
						: GriddingParamsSetted.InterpolationMethod.SPLINES,
				GriddingParamsSetted.DEFAULT_POWER,
				GriddingParamsSetted.DEFAULT_MIN_POINTS,
				Boolean.parseBoolean(filterInputs.get(Filter.gridding_analytic_signal_enabled.name()).getText()),
				Boolean.parseBoolean(filterInputs.get(Filter.gridding_hillshading_enabled.name()).getText()),
				Boolean.parseBoolean(filterInputs.get(Filter.gridding_smoothing_enabled.name()).getText()),
				Double.parseDouble(filterInputs.get(Filter.gridding_hillshading_azimuth.name()).getText()),
				Double.parseDouble(filterInputs.get(Filter.gridding_hillshading_altitude.name()).getText()),
				Double.parseDouble(filterInputs.get(Filter.gridding_hillshading_intensity.name()).getText())
		));
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

	private @NonNull StackPane createFilterOptions(Filter filter, String prompt, FilterActions actions) {
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
					prefSettings.saveSetting(filter.name(),
							((CsvFile) selectedFile).getParser().getTemplate().getName(),
							filterInput.getText());
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
					prefSettings.saveSetting(filter.name(),
							((CsvFile) selectedFile).getParser().getTemplate().getName(),
							filterInput.getText());
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
		var chart = model.getCsvChart((CsvFile) selectedFile);
		chart.ifPresent(c -> c.gnssTimeLag(c.getSelectedSeriesName(), value));
		model.publishEvent(new WhatChanged(this, WhatChanged.Change.csvDataFiltered));
	}

	private void applyGnssTimeLagToAll(int value) {
		var chart = model.getCsvChart((CsvFile) selectedFile);
		chart.ifPresent(sc -> {
			String seriesName = sc.getSelectedSeriesName();
			model.getCsvCharts().stream()
					.filter(c -> c.isSameTemplate((CsvFile) selectedFile))
					.forEach(c -> c.gnssTimeLag(seriesName, value));
		});
		model.publishEvent(new WhatChanged(this, WhatChanged.Change.csvDataFiltered));
	}

	private void applyLowPassFilter(int value) {
		var chart = model.getCsvChart((CsvFile) selectedFile);
		chart.ifPresent(c -> c.lowPassFilter(c.getSelectedSeriesName(), value));
		showGridInputDataChangedWarning(true);
	}

	private void applyLowPassFilterToAll(int value) {
		var chart = model.getCsvChart((CsvFile) selectedFile);
		chart.ifPresent(sc -> {
			String seriesName = sc.getSelectedSeriesName();
			model.getCsvCharts().stream()
					.filter(c -> c.isSameTemplate((CsvFile) selectedFile))
					.forEach(c -> c.lowPassFilter(seriesName, value));
		});
		showGridInputDataChangedWarning(true);
	}

	private void applyMedianCorrection(int value) {
		var chart = model.getCsvChart((CsvFile) selectedFile);
		var rangeBefore = getSelectedSeriesRange();
		chart.ifPresent(c -> c.medianCorrection(c.getSelectedSeriesName(), value));
		Platform.runLater(() -> updateGriddingMinMaxPreserveUserRange(rangeBefore));
		showGridInputDataChangedWarning(true);
	}

	private void applyMedianCorrectionToAll(int value) {
		var chart = model.getCsvChart((CsvFile) selectedFile);
		var rangeBefore = getSelectedSeriesRange();
		chart.ifPresent(sc -> {
			String seriesName = sc.getSelectedSeriesName();
			model.getCsvCharts().stream()
					.filter(c -> c.isSameTemplate((CsvFile) selectedFile))
					.forEach(c -> c.medianCorrection(seriesName, value));
		});
		Platform.runLater(() -> updateGriddingMinMaxPreserveUserRange(rangeBefore));
		showGridInputDataChangedWarning(true);
	}

	private void toggleQualityLayer(boolean active) {
		QualityLayer qualityLayer = mapView.getQualityLayer();
		if (qualityLayer.isActive() == active) {
			return;
		}
		qualityLayer.setActive(active);
		model.publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
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
			model.publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
		}
	}

	private void applyQualityControlToAll(QualityControlParams params) {
		QualityControl qualityControl = new QualityControl();
		List<CsvFile> files = model.getFileManager().getCsvFiles();
		List<QualityCheck> checks = createQualityChecks(params);
		List<QualityIssue> issues = qualityControl.getQualityIssues(files, checks);

		mapView.getQualityLayer().setIssues(issues);
		model.publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
	}

	private Tab prepareGprTab(Tab gprTab, TraceFile file) {

		// background
		StackPane backgroundOptions = createGprBackgroundOptions(file);
		ToggleButton backgroundToggle = new ToggleButton("Background");
		backgroundToggle.setMaxWidth(Double.MAX_VALUE);
		backgroundToggle.setOnAction(getChangeVisibleAction(backgroundOptions));

		// gridding
		StackPane griddingOptions = createGprGriddingOptions(file);
		ToggleButton griddingToggle = new ToggleButton("Gridding");
		griddingToggle.setMaxWidth(Double.MAX_VALUE);
		griddingToggle.setOnAction(getChangeVisibleAction(griddingOptions));

		// elevation
		StackPane elevationOptions = createGprElevationOptions(file);
		ToggleButton elevationToggle = new ToggleButton("Elevation");
		elevationToggle.setMaxWidth(Double.MAX_VALUE);
		elevationToggle.setOnAction(getChangeVisibleAction(elevationOptions));

		scriptExecutionView = new ScriptExecutionView(model, status, selectedFile, pythonScriptExecutorService);
		StackPane scriptsPane = new StackPane(scriptExecutionView);
		ToggleButton scriptsButton = new ToggleButton("Scripts");
		scriptsButton.setMaxWidth(Double.MAX_VALUE);
		scriptsButton.setOnAction(getChangeVisibleAction(scriptsPane));

		VBox container = new VBox();
		container.setPadding(new Insets(10, 8, 10, 8));
		container.setSpacing(DEFAULT_SPACING);

		container.getChildren().addAll(
				backgroundToggle, backgroundOptions,
				griddingToggle, griddingOptions,
				elevationToggle, elevationOptions,
				scriptsButton, scriptsPane);

		ScrollPane scrollContainer = createVerticalScrollContainer(container);
		gprTab.setContent(scrollContainer);
		return gprTab;
	}

	private StackPane createGprBackgroundOptions(TraceFile file) {
		VBox options = new VBox(DEFAULT_SPACING);
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
		VBox options = new VBox(DEFAULT_SPACING);
		options.setPadding(DEFAULT_GPR_OPTIONS_INSETS);

		// gpr gridding
		options.getChildren().addAll(mapView.getRight(file));

		options.setVisible(false);
		options.setManaged(false);

		return new StackPane(options);
	}

	private StackPane createGprElevationOptions(TraceFile file) {
		VBox options = new VBox(DEFAULT_SPACING);
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
			if (statisticsView != null) {
				Platform.runLater(() -> {
					statisticsView.update(event.getFile());
				});
			}
			if (scriptExecutionView != null) {
				scriptExecutionView.updateView(event.getFile());
			}
			//setGriddingMinMax();
			Platform.runLater(this::updateGriddingMinMaxPreserveUserRange);
			setSavedFilterInputValue(Filter.lowpass);
            setSavedFilterInputValue(Filter.timelag);
            setSavedFilterInputValue(Filter.gridding_cellsize);
            setSavedFilterInputValue(Filter.gridding_blankingdistance);
			// Load analytic signal parameters
			setSavedFilterInputValue(Filter.gridding_analytic_signal_enabled);
            // Load hill-shading parameters
            setSavedFilterInputValue(Filter.gridding_hillshading_enabled);
            setSavedFilterInputValue(Filter.gridding_hillshading_azimuth);
            setSavedFilterInputValue(Filter.gridding_hillshading_altitude);
            setSavedFilterInputValue(Filter.gridding_hillshading_intensity);

			setSavedFilterInputValue(Filter.gridding_smoothing_enabled);
			//TODO: check if possible, that current file and sensor was not gridded with current parameters {
				showGridInputDataChangedWarning(true);
			//}

			setSavedFilterInputValue(Filter.median_correction);
			setSavedFilterInputValue(Filter.quality_max_line_distance);
			setSavedFilterInputValue(Filter.quality_line_distance_tolerance);
			setSavedFilterInputValue(Filter.quality_max_altitude);
			setSavedFilterInputValue(Filter.quality_altitude_tolerance);
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

	private void setSavedFilterInputValue(Filter filter) {
		var savedValue = prefSettings.getSetting(filter.name(), ((CsvFile) selectedFile).getParser().getTemplate().getName());
		if (savedValue != null) {
			filterInputs.get(filter.name()).setText(savedValue);
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

	public ToggleButton getGridding() {
		return gridding;
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
			Double maxLineDistanceValue = getTextAsDouble(maxLineDistance);
			if (maxLineDistanceValue != null && maxLineDistanceValue <= 0.0) {
				maxLineDistanceValue = null;
			}
			Double lineDistanceToleranceValue = getTextAsDouble(lineDistanceTolerance);
			Double maxAltitudeValue = getTextAsDouble(maxAltitude);
			if (maxAltitudeValue != null && maxAltitudeValue <= 0.0) {
				maxAltitudeValue = null;
			}
			Double altitudeToleranceValue = getTextAsDouble(altitudeTolerance);
			return new QualityControlParams(
					maxLineDistanceValue, lineDistanceToleranceValue, maxAltitudeValue, altitudeToleranceValue);
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
						templateName,
						maxLineDistance.getText());
				prefSettings.saveSetting(
						Filter.quality_line_distance_tolerance.name(),
						templateName,
						lineDistanceTolerance.getText());
				prefSettings.saveSetting(
						Filter.quality_max_altitude.name(),
						templateName,
						maxAltitude.getText());
				prefSettings.saveSetting(
						Filter.quality_altitude_tolerance.name(),
						templateName,
						altitudeTolerance.getText());
			}
		}
	}
}
