package com.ugcs.gprvisualizer.gpr;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import com.github.thecoldwine.sigrun.common.ext.TraceFile;
import com.github.thecoldwine.sigrun.common.ext.TraceKey;
import com.ugcs.gprvisualizer.app.*;
import com.ugcs.gprvisualizer.app.auxcontrol.*;
import com.ugcs.gprvisualizer.app.events.FileClosedEvent;
import com.ugcs.gprvisualizer.app.parcers.GeoData;
import com.ugcs.gprvisualizer.app.service.TemplateSettingsModel;
import com.ugcs.gprvisualizer.event.BaseEvent;
import com.ugcs.gprvisualizer.event.FileSelectedEvent;
import com.ugcs.gprvisualizer.event.TemplateUnitChangedEvent;
import com.ugcs.gprvisualizer.event.WhatChanged;
import com.ugcs.gprvisualizer.utils.FileTemplate;
import com.ugcs.gprvisualizer.utils.FileTypes;
import com.ugcs.gprvisualizer.utils.Nulls;
import com.ugcs.gprvisualizer.utils.Traces;
import javafx.scene.layout.*;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.github.thecoldwine.sigrun.common.ext.CsvFile;
import com.github.thecoldwine.sigrun.common.ext.FileChangeType;
import com.github.thecoldwine.sigrun.common.ext.LatLon;
import com.github.thecoldwine.sigrun.common.ext.MapField;
import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.ugcs.gprvisualizer.app.ext.FileManager;
import com.ugcs.gprvisualizer.math.MinMaxAvg;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ScrollPane;
import javafx.scene.paint.Color;

@Component
public class Model implements InitializingBean {

	public static final int TOP_MARGIN = 60;

	public static final int CHART_MIN_HEIGHT = 400;

	private static final List<Color> BRIGHT_COLORS = List.of(
			Color.web("#fbd101"),
			//Color.web("#fdff0d"),
			Color.web("#b0903a"),
			Color.web("#f99e01"),
			Color.web("#c56a04"),
			Color.web("#df818e"),
			Color.web("#ff6455"),
			Color.web("#d80b01"),
			Color.web("#b40a13"),
			Color.web("#690d08"),
			Color.web("#989d9b"),
			Color.web("#738768"),
			Color.web("#6bb7e6"),
			Color.web("#496a8b"),
			Color.web("#2b52a3"),
			Color.web("#272f73"),
			Color.web("#5b9a95"),
			Color.web("#add6aa"),
			Color.web("#2b960a"),
			Color.web("#0e5f1e"),
			Color.web("#bfc1c3"),
			Color.web("#cbac7a"),
			Color.web("#80674e"),
			Color.web("#cabf95"),
			Color.web("#7b7b7b"),
			Color.web("#354a32"),
			Color.web("#8c2a07"),
			Color.web("#545a4c"),
			Color.web("#242d29"),
			Color.web("#7b7b7b"));

	private static final Logger log = LoggerFactory.getLogger(Model.class);

	private final Random rand = new Random();

	@SuppressWarnings("NullAway.Init")
	@Value("${trace.lookup.threshold}")
	private Double traceLookupThreshold;

	private boolean loading = false;

	private final MapField field = new MapField();

	private final FileManager fileManager;

	private final Set<FileChangeType> changes = new HashSet<>();

	private final List<BaseObject> auxElements = new ArrayList<>();

	private final List<TraceKey> selectedTraces = new ArrayList<>();

	private boolean kmlToFlagAvailable = false;

	private final PrefSettings prefSettings;

	private final Map<String, Color> headerColors = new HashMap<>();

	private final AuxElementEditHandler auxEditHandler;

	private final VBox chartsContainer = new VBox(); // Charts container

	private final Map<CsvFile, SensorLineChart> csvFiles = new HashMap<>();

	private final Map<TraceFile, GPRChart> gprCharts = new HashMap<>();

	private final ApplicationEventPublisher eventPublisher;

	private final TemplateSettingsModel templateSettingsModel;

	@Nullable
	private Node selectedDataNode;

	@Nullable
	private SgyFile currentFile;

	public Model(FileManager fileManager, PrefSettings prefSettings, ApplicationEventPublisher eventPublisher, TemplateSettingsModel templateSettingsModel) {
		this.prefSettings = prefSettings;
		this.fileManager = fileManager;
		this.auxEditHandler = new AuxElementEditHandler(this);
		this.eventPublisher = eventPublisher;
		this.templateSettingsModel = templateSettingsModel;
	}

	public AuxElementEditHandler getAuxEditHandler() {
		return auxEditHandler;
	}

	public MapField getMapField() {
		return field;
	}

	public FileManager getFileManager() {
		return fileManager;
	}

	@Nullable
	public SgyFile getCurrentFile() {
		return currentFile;
	}

	public Set<FileChangeType> getChanges() {
		return changes;
	}

	public List<BaseObject> getAuxElements() {
		List<BaseObject> combinedElements = new ArrayList<>(auxElements);
		combinedElements.addAll(gprCharts.values().stream()
				.flatMap(gprChart -> gprChart.getAuxElements().stream())
				.toList());
		return List.copyOf(combinedElements);
	}

	public void updateAuxElements() {
		gprCharts.values().forEach(
				GPRChart::updateAuxElements
		);

		auxElements.clear();
		getFileManager().getCsvFiles().forEach(sf -> {
			auxElements.addAll(sf.getAuxElements());
			getCsvChart(sf).ifPresent(chart ->
					sf.getAuxElements().forEach(element -> {
								if (element instanceof FoundPlace foundPlace) {
									chart.addFlag(foundPlace);
								}
							}
					));
		});
	}

	public VBox getChartsContainer() {
		return chartsContainer;
	}

	public boolean isLoading() {
		return loading;
	}

	public void setLoading(boolean loading) {
		this.loading = loading;
	}

	public void initField() {
		// center
		MinMaxAvg lonMid = new MinMaxAvg();
		MinMaxAvg latMid = new MinMaxAvg();

		for (SgyFile file : fileManager.getFiles()) {
			for (GeoData value : Nulls.toEmpty(file.getGeoData())) {
				LatLon latlon = value.getLatLon();
				if (latlon == null) {
					continue;
				}
				latMid.put(latlon.getLatDgr());
				lonMid.put(latlon.getLonDgr());
			}
		}

		if (latMid.isNotEmpty()) {
			this.getMapField().setPathCenter(
					new LatLon(latMid.getMid(), lonMid.getMid()));
			this.getMapField().setSceneCenter(
					new LatLon(latMid.getMid(), lonMid.getMid()));

			LatLon lt = new LatLon(latMid.getMin(), lonMid.getMin());
			LatLon rb = new LatLon(latMid.getMax(), lonMid.getMax());

			this.getMapField().setPathEdgeLL(lt, rb);

			this.getMapField().adjustZoom(CHART_MIN_HEIGHT, 700);

		} else {
			//Sout.p("GPS coordinates not found");
			//this.getMapField().setPathCenter(null);
			//this.getMapField().setSceneCenter(null);
		}
	}

	public void init() {
		this.updateAuxElements();
	}

	public boolean isActive() {
		return getFileManager().isActive();
	}

	public boolean stopUnsaved() {
		if (getFileManager().isUnsavedExists()) {

			Alert alert = new Alert(AlertType.CONFIRMATION);
			alert.setTitle("Warning");
			alert.setHeaderText("Current files are not saved. Continue?");

			Optional<ButtonType> result = alert.showAndWait();

			if (!result.isPresent() || result.get() != ButtonType.OK) {
				return true;
			}
		}
		return false;
	}

	public boolean isSpreadCoordinatesNecessary() {
		for (TraceFile file : getFileManager().getGprFiles()) {
			if (file.isSpreadCoordinatesNecessary()) {
				return true;
			}
		}
		return false;
	}

	public boolean isKmlToFlagAvailable() {
		return kmlToFlagAvailable;
	}

	public void setKmlToFlagAvailable(boolean kmlToFlagAvailable) {
		this.kmlToFlagAvailable = kmlToFlagAvailable;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		AppContext.model = this;
		loadColorSettings(headerColors);
	}

	/**
	 * Initialize chart for the given CSV file
	 *
	 * @param csvFile CSV file to initialize chart for
	 */
	public void initCsvChart(CsvFile csvFile) {
		if (getCsvChart(csvFile).isPresent()) {
			return;
		}
		var sensorLineChart = createSensorLineChart(csvFile);
		saveColorSettings(headerColors);

		Platform.runLater(() -> selectAndScrollToChart(sensorLineChart));
	}

	public void updateCsvChart(CsvFile csvFile) {
		Optional<SensorLineChart> csvChart = getCsvChart(csvFile);
		if (csvChart.isEmpty()) {
			return;
		}

		csvChart.get().reload();
	}

	public void updateCsvChartFile(CsvFile csvFile, File file) {
		SensorLineChart csvChart = csvFiles.remove(csvFile);
		csvFile.setFile(file);
		if (csvChart != null) {
			csvFiles.put(csvFile, csvChart);
		}
	}

	public void recreateCsvChart(CsvFile csvFile) {
		// Remove the old chart if it exists
		getFileManager().removeFile(csvFile);
		Optional<SensorLineChart> oldChartOpt = getCsvChart(csvFile);
		oldChartOpt.ifPresent(oldChart -> {
			Node oldNode = oldChart.getRootNode();
			chartsContainer.getChildren().remove(oldNode);
			csvFiles.remove(csvFile);
		});

		// Create and add the new chart
		SensorLineChart newChart = createSensorLineChart(csvFile);
		saveColorSettings(headerColors);
		getFileManager().addFile(csvFile);

		Platform.runLater(() -> selectAndScrollToChart(newChart));
	}

	private SensorLineChart createSensorLineChart(CsvFile csvFile) {
		// if there is a chart for a given file then put new chart
		// to the same position as it was before
		int index = -1;

		SensorLineChart chart = csvFiles.get(csvFile);
		if (chart != null) {
			Node chartBox = chart.getRootNode();
			if (chartBox != null) {
				index = chartsContainer.getChildren().indexOf(chartBox);
			}
		}

		chart = new SensorLineChart(this, eventPublisher, templateSettingsModel);
		csvFiles.remove(csvFile);
		csvFiles.put(csvFile, chart);

		// create new chart contents
		var plotData = chart.generatePlotData(csvFile);
		var newChartBox = chart.createChartWithMultipleYAxes(csvFile, plotData);

		// add to container keeping position
		if (index != -1) {
			chartsContainer.getChildren().set(index, newChartBox);
		} else {
			chartsContainer.getChildren().add(newChartBox);
		}

		// adjust height
		newChartBox.getChildren().forEach(node -> {
			if (node instanceof StackPane) {
				((StackPane) node).setPrefHeight(Math.max(CHART_MIN_HEIGHT, node.getScene().getHeight()));
				((StackPane) node).setMinHeight(Math.max(CHART_MIN_HEIGHT, node.getScene().getHeight() / 2));
			}
		});

		return chart;
	}

	private void saveColorSettings(Map<String, Color> headerColors) {
		String group = "colors";
		prefSettings.saveSetting(group, headerColors);
	}

	/**
	 * Get chart for the given file if it exists in the model
	 *
	 * @param csvFile CSV file to get chart for
	 * @return Optional of SensorLineChart
	 */
	public Optional<SensorLineChart> getCsvChart(CsvFile csvFile) {
		return Optional.ofNullable(csvFiles.get(csvFile));
	}

	public Collection<SensorLineChart> getCsvCharts() {
		return csvFiles.values();
	}

	public void chartsZoomOut() {
		csvFiles.forEach((file, chart) -> {
			chart.zoomToFit();
		});
	}

	public void closeAllCharts() {
		csvFiles.forEach((file, chart) -> {
			chart.close(false);
		});
		csvFiles.clear();
		gprCharts.clear();
	}

	public void removeChart(SgyFile file) {
		if (file instanceof CsvFile csvFile) {
			csvFiles.remove(csvFile);
		} else if (file instanceof TraceFile traceFile) {
			gprCharts.remove(traceFile);
		}
		// select first file in a list
		boolean chartSelected = false;
		List<SgyFile> openedFiles = fileManager.getFiles();
		if (!openedFiles.isEmpty()) {
			Chart chart = getFileChart(openedFiles.getFirst());
			if (chart != null) {
				selectAndScrollToChart(chart);
				chartSelected = true;
			}
		}
		if (!chartSelected) {
			publishEvent(new FileSelectedEvent(this, (SgyFile) null));
		}
	}

	public void loadColorSettings(Map<String, Color> headerColors) {
		var colors = prefSettings.getAllSettings().get("colors");
		if (colors != null)
			colors.forEach((key, value) -> {
				headerColors.put(key, Color.web(value));
			});
	}

	public Color getColorByHeader(String header) {
		return headerColors.computeIfAbsent(header, k -> generateRandomColor());
	}

	// Generate random color
	private Color generateRandomColor() {
		return BRIGHT_COLORS.get(rand.nextInt(BRIGHT_COLORS.size()));
	}

	private void setSelectedData(Node node) {
		this.selectedDataNode = node;
	}

	@Nullable
	private Node getSelectedData() {
		return selectedDataNode;
	}

	public boolean isChartSelected(@Nullable FileDataContainer fileDataContainer) {
		if (fileDataContainer == null) {
			return false;
		}
		Node root = fileDataContainer.getRootNode();
		return root != null && root.equals(selectedDataNode);
	}

	public boolean selectChart(FileDataContainer fileDataContainer) {
		Node node = fileDataContainer.getRootNode();

		if (getSelectedData() != null) {
			if (getSelectedData() == node) {
				fileDataContainer.selectFile();
				return false;
			}
			// clear selection
			getSelectedData().setStyle("-fx-border-width: 2px; -fx-border-color: transparent;");
		}

		node.setStyle("-fx-border-width: 2px; -fx-border-color: lightblue;");
		setSelectedData(node);

		fileDataContainer.selectFile();

		return true;
	}

	public void scrollToChart(@Nullable FileDataContainer fileDataContainer) {
		if (fileDataContainer == null) {
			return;
		}
		Node node = fileDataContainer.getRootNode();
		ScrollPane scrollPane = findScrollPane(node);
		if (scrollPane != null) {
			//TODO: implement scroll to chart
			scrollToChart(scrollPane, node);
		}
	}

	public boolean selectAndScrollToChart(FileDataContainer fileDataContainer) {
		boolean selected = selectChart(fileDataContainer);
		scrollToChart(fileDataContainer);
		return selected;
	}

	@Nullable
	private ScrollPane findScrollPane(Node node) {
		Parent parent = node.getParent();
		while (parent != null) {
			if (parent instanceof ScrollPane) {
				return (ScrollPane) parent;
			}
			parent = parent.getParent();
		}
		return null;
	}

	private void scrollToChart(ScrollPane scrollPane, Node chart) {
		Bounds viewportBounds = scrollPane.getViewportBounds();
		Bounds chartBounds = chart.getBoundsInParent();

		double heightDifference = chartsContainer.getBoundsInParent().getHeight() - viewportBounds.getHeight();

		double vValue = chartBounds.getMinY() / heightDifference;

		scrollPane.setVvalue(vValue < 0 ? Double.POSITIVE_INFINITY : vValue);
	}

	public void focusMapOnTrace(TraceKey trace) {
		if (trace == null) {
			return;
		}

		field.setSceneCenter(trace.getLatLon());
		eventPublisher.publishEvent(new WhatChanged(this, WhatChanged.Change.mapscroll));
	}

	public void publishEvent(BaseEvent event) {
		eventPublisher.publishEvent(event);
	}

	@Nullable
	public GPRChart getGprChart(TraceFile sgyFile) {
		return gprCharts.get(sgyFile);
	}

	public GPRChart getOrCreateGprChart(@NonNull TraceFile file) {
		GPRChart chart = gprCharts.get(file);
		if (chart != null) {
			return chart;
		}
		chart = new GPRChart(this, file, templateSettingsModel);
		gprCharts.put(file, chart);
		return chart;
	}

	@EventListener
	private void onChange(WhatChanged event) {
		if (event.isJustdraw()) {
			csvFiles.values().forEach(chart ->
					Platform.runLater(chart::updateChartName)
			);
			gprCharts.values().forEach(chart -> {
				Platform.runLater(chart::repaint);
			});
		}
	}

	@EventListener
	private void fileSelected(FileSelectedEvent event) {
		currentFile = event.getFile();
	}

	@EventListener
	private void fileClosed(FileClosedEvent event) {
		Chart chart = getFileChart(event.getSgyFile());
		clearSelectedTrace(chart);
	}

	@EventListener
	public void onTemplateUnitChanged(TemplateUnitChangedEvent event) {
		if (event.getUnit() == null || event.getTemplateName() == null || event.getFile() == null) {
			return;
		}
		File eventFile = event.getFile().getFile();
		if (FileTypes.isCsvFile(eventFile)) {
			for (Map.Entry<CsvFile, SensorLineChart> entry : csvFiles.entrySet()) {
				CsvFile csvFile = entry.getKey();
				SensorLineChart chart = entry.getValue();
				File file = csvFile.getFile();
				if (file == null || Objects.equals(csvFile, event.getFile())) {
					continue;
				}
				if (Objects.equals(FileTemplate.getTemplateName(this, file), event.getTemplateName())) {
					chart.updateXAxisUnits(event.getUnit());
					Platform.runLater(chart::reload);
				}
			}
			return;
		}
		if (FileTypes.isGprFile(eventFile)) {
			for (Map.Entry<TraceFile, GPRChart> entry : gprCharts.entrySet()) {
				TraceFile traceFile = entry.getKey();
				GPRChart chart = entry.getValue();
				File file = traceFile.getFile();
				if (file == null || Objects.equals(traceFile, event.getFile())) {
					continue;
				}
				if (Objects.equals(FileTemplate.getTemplateName(this, file), event.getTemplateName())) {
					HorizontalRulerController controller = chart.getHorizontalRulerController();
					controller.setUnit(event.getUnit());
					Platform.runLater(chart::repaint);
				}
			}
		}
	}

	// charts

	public List<Chart> getAllFileCharts() {
		List<Chart> charts = new ArrayList<>();
		charts.addAll(csvFiles.values());
		charts.addAll(gprCharts.values());
		return charts;
	}

	@Nullable
	public Chart getFileChart(@Nullable SgyFile file) {
		if (file instanceof CsvFile csvFile) {
			return getCsvChart(csvFile).orElse(null);
		}
		if (file instanceof TraceFile traceFile) {
			return getGprChart(traceFile);
		}
		return null;
	}

	// trace selection

	public List<TraceKey> getSelectedTraces() {
		return Collections.unmodifiableList(selectedTraces);
	}

	@Nullable
	public TraceKey getSelectedTrace(Chart chart) {
		if (chart == null) {
			return null;
		}
		for (TraceKey trace : selectedTraces) {
			if (Objects.equals(chart, getFileChart(trace.getFile()))) {
				return trace;
			}
		}
		return null;
	}

	@Nullable
	public TraceKey getSelectedTraceInCurrentChart() {
		Chart chart = getFileChart(currentFile);
		return chart != null
				? getSelectedTrace(chart)
				: null;
	}

	public void selectNearestTrace(LatLon location) {
		if (location == null) {
			return;
		}
		Optional<TraceKey> nearestTrace = Traces.findNearestTraceInFiles(fileManager.getFiles(), location);
		nearestTrace.ifPresent(trace -> {
			selectTrace(trace, true);
		});
	}

	public void selectTrace(TraceKey trace) {
		selectTrace(trace, false);
	}

	public void selectTrace(TraceKey trace, boolean focusOnChart) {
		if (trace == null) {
			return;
		}

		selectedTraces.clear();
		selectedTraces.add(trace);

		Chart traceChart = getFileChart(trace.getFile());
		boolean traceOnSelectedChart = isChartSelected(traceChart);

		for (Chart chart : getAllFileCharts()) {
			if (Objects.equals(chart, traceChart)) {
				continue;
			}
			Optional<TraceKey> nearestInChart = Traces.findNearestTrace(
					chart.getFile(),
					trace.getLatLon(),
					traceLookupThreshold);
			if (nearestInChart.isPresent()) {
				selectedTraces.add(nearestInChart.get());
				traceOnSelectedChart = traceOnSelectedChart || isChartSelected(chart);
			}
		}

		boolean keepSelection = traceOnSelectedChart;
		Platform.runLater(() -> {
			for (Chart chart : getAllFileCharts()) {
				boolean focusOnTrace = focusOnChart || !Objects.equals(chart, traceChart);
				updateSelectedTraceOnChart(chart, focusOnTrace);
			}
			if (focusOnChart) {
				if (keepSelection) {
					scrollToChart(traceChart);
				} else {
					if (traceChart != null) {
						selectAndScrollToChart(traceChart);
					}
				}
			}
		});
	}

	public void clearSelectedTrace(@Nullable Chart chart) {
		selectedTraces.removeIf(x -> Objects.equals(chart, getFileChart(x.getFile())));
		Platform.runLater(() -> updateSelectedTraceOnChart(chart, false));
	}

	public void clearSelectedTraces() {
		selectedTraces.clear();
		Platform.runLater(() -> {
			for (Chart chart : getAllFileCharts()) {
				updateSelectedTraceOnChart(chart, false);
			}
		});
	}

	private void updateSelectedTraceOnChart(@Nullable Chart chart, boolean focusOnTrace) {
		if (chart == null) {
			return;
		}
		TraceKey trace = getSelectedTrace(chart);
		chart.selectTrace(trace, focusOnTrace);
		publishEvent(new WhatChanged(this, WhatChanged.Change.traceSelected));
	}

	// flags

	public void createFlagOnSelection(Chart chart) {
		if (chart == null) {
			return;
		}

		TraceKey selectedTrace = getSelectedTrace(chart);
		if (selectedTrace == null) {
			return; // no trace selected in a chart
		}
		FoundPlace flag = new FoundPlace(selectedTrace, this);
		flag.setSelected(true);

		// clear current selection in file
		chart.selectFlag(null);
		chart.addFlag(flag);

		SgyFile traceFile = selectedTrace.getFile();
		traceFile.getAuxElements().add(flag);
		traceFile.setUnsaved(true);

		clearSelectedTrace(chart);

		updateAuxElements();
		publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
	}

	public void removeSelectedFlag(Chart chart) {
		if (chart == null) {
			return;
		}

		List<FoundPlace> flags = chart.getFlags();
		var selectedFlag = flags.stream()
				.filter(FoundPlace::isSelected)
				.findFirst();

		if (selectedFlag.isEmpty()) {
			return;
		}

		chart.removeFlag(selectedFlag.get());

		SgyFile traceFile = selectedFlag.get().getTrace().getFile();
		traceFile.getAuxElements().remove(selectedFlag.get());
		traceFile.setUnsaved(true);

		updateAuxElements();
		publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
	}

	public void removeAllFlags(Chart chart) {
		if (chart == null) {
			return;
		}

		chart.clearFlags();
		SgyFile file = chart.getFile();
		boolean modified = file.getAuxElements().removeIf(
				x -> x instanceof FoundPlace);
		if (modified) {
			file.setUnsaved(true);
		}
		updateAuxElements();
		publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
	}
}
