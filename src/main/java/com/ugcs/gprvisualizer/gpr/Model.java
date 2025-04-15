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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.thecoldwine.sigrun.common.ext.GprFile;
import com.ugcs.gprvisualizer.app.*;
import com.ugcs.gprvisualizer.app.auxcontrol.*;
import com.ugcs.gprvisualizer.app.events.FileClosedEvent;
import com.ugcs.gprvisualizer.event.BaseEvent;
import com.ugcs.gprvisualizer.event.FileSelectedEvent;
import com.ugcs.gprvisualizer.event.WhatChanged;
import com.ugcs.gprvisualizer.utils.TraceUtils;
import javafx.scene.layout.*;
import org.jetbrains.annotations.NotNull;
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
import com.github.thecoldwine.sigrun.common.ext.Trace;
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

	public static final int TOP_MARGIN = 50;
	public static final int CHART_MIN_HEIGHT = 400;

	private static final Logger log = LoggerFactory.getLogger(Model.class);

	@Value( "${trace.lookup.threshold}" )
	private Double traceLookupThreshold;

	private boolean loading = false; 
	
	private final MapField field = new MapField();

	private final FileManager fileManager;

	private final Set<FileChangeType> changes = new HashSet<>();
	
	private final List<BaseObject> auxElements = new ArrayList<>();

	private final List<ClickPlace> selectedTraces = new ArrayList<>();

	private boolean kmlToFlagAvailable = false;

	private final PrefSettings prefSettings;

	private final AuxElementEditHandler auxEditHandler;

	private final VBox chartsContainer = new VBox(); // Charts container

	private final Map<CsvFile, SensorLineChart> csvFiles = new HashMap<>();

	private final Map<SgyFile, GPRChart> gprCharts = new HashMap<>();

	private final ApplicationEventPublisher eventPublisher;

	private SgyFile currentFile;

	public Model(FileManager fileManager, PrefSettings prefSettings, ApplicationEventPublisher eventPublisher) {
		this.prefSettings = prefSettings;
		this.fileManager = fileManager;
		this.auxEditHandler = new AuxElementEditHandler(this);
		this.eventPublisher = eventPublisher;
	}

	public AuxElementEditHandler getAuxEditHandler() {
		return auxEditHandler;
	}

	//public Settings getSettings() {
		//return gprChart.getField().getProfileSettings();
	//}
		
	/*public void setBounds(Rectangle2D.Double bounds) {
		this.bounds = bounds;		
	}
	
	public Rectangle2D.Double getBounds() {
		return bounds;
	}*/

	public MapField getMapField() {
		return field;
	}

	public FileManager getFileManager() {
		return fileManager;
	}

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
            sf.getAuxElements().forEach(element -> {
                getChart((CsvFile) sf).get().addFlag((FoundPlace) element);
            });
        });
	}

	public VBox getChartsContainer() {
		return chartsContainer;
	}

	public GPRChart getProfileField(SgyFile sgyFile) {
		if (!gprCharts.containsKey(sgyFile)) {
			System.out.println(sgyFile + " not found in gprCharts");
		}
		return gprCharts.get(sgyFile); //computeIfAbsent(sgyFile, f -> new GPRChart(this, auxEditHandler, f));
		//return gprChart;
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
		for (Trace trace : getTraces()) {
			if (trace == null) {
				System.out.println("null trace or ot latlon");
				continue;
			}
			
			if (trace.getLatLon() != null) {
				latMid.put(trace.getLatLon().getLatDgr());
				lonMid.put(trace.getLatLon().getLonDgr());
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
		//
		//
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
		for (SgyFile file : getFileManager().getGprFiles()) {
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
		loadColorSettings(semanticColors);
	}

	/** 
	 * Initialize chart for the given CSV file
	 * @param csvFile CSV file to initialize chart for
	 * @return void
	 */
	public void initChart(CsvFile csvFile) {
		if (getChart(csvFile).isPresent()) {
			return;
		}
		var sensorLineChart = createSensorLineChart(csvFile);
		saveColorSettings(semanticColors);

		Platform.runLater(() -> {
			selectAndScrollToChart(sensorLineChart);
		});
	}

	public void updateChart(CsvFile csvFile) {
		Optional<SensorLineChart> currentChart = getChart(csvFile);
		if (currentChart.isEmpty()) {
			return;
		}

		var sensorLineChart = createSensorLineChart(csvFile);
		Platform.runLater(() -> {
			selectChart(sensorLineChart);
		});
	}

	public void updateChartFile(CsvFile csvFile, File file) {
		SensorLineChart chart = csvFiles.remove(csvFile);
		csvFile.setFile(file);
		if (chart != null) {
			csvFiles.put(csvFile, chart);
		}
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

		chart = new SensorLineChart(this, eventPublisher, prefSettings, auxEditHandler);
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

	private void saveColorSettings(Map<String, Color> semanticColors) {
		String group = "colors";
		prefSettings.saveSetting(group, semanticColors);
	}

	/**
	 * Get chart for the given file if it exists in the model
	 * @param csvFile CSV file to get chart for
	 * @return Optional of SensorLineChart
	 */
    public Optional<SensorLineChart> getChart(CsvFile csvFile) {
		return Optional.ofNullable(csvFiles.get(csvFile));
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

	public void removeChart(SgyFile sgyFile) {
		if (sgyFile instanceof CsvFile csvFile) {
			csvFiles.remove(csvFile);
			if (!csvFiles.isEmpty()) {
				selectAndScrollToChart(csvFiles.values().stream().toList().getFirst());
			} else {
				publishEvent(new FileSelectedEvent(this, (SgyFile) null));
			}

		} else {
			gprCharts.remove(sgyFile);
		}
    }

	Map<String, Color> semanticColors = new HashMap<>();

	public void loadColorSettings(Map<String, Color> semanticColors) {
		prefSettings.getAllSettings().get("colors")
			.forEach((key, value) -> {
			//if (key.startsWith("colors")) {
				semanticColors.put(key, Color.web(value));
			//}
		});
	}

	public Color getColorBySemantic(String semantic) {
		return semanticColors.computeIfAbsent(semantic, k -> generateRandomColor());
	}

	private List<Color> brightColors = List.of(
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

	Random rand = new Random();

	private Node selectedDataNode;

	// Generate random color
    private Color generateRandomColor() {
        return brightColors.get(rand.nextInt(brightColors.size()));
    }

	/*public boolean isOnlyCsvLoaded() {
		return fileManager.getFiles().stream()
				.allMatch(SgyFile::isCsvFile);
	}*/

	public List<Trace> getGprTraces() {
		return getFileManager().getGprTraces();
	}

	public List<Trace> getCsvTraces() {
		return getFileManager().getCsvTraces();
	}

	public List<Trace> getTraces() {
		List<Trace> result = new ArrayList<>();
		result.addAll(getGprTraces());
		result.addAll(getCsvTraces());
		return result;
	}

	private void setSelectedData(Node node) {
		this.selectedDataNode = node;
	}

    private Node getSelectedData() {
		return selectedDataNode;   
	}

	public boolean selectChart(FileDataContainer fileDataContainer) {
		Node node = fileDataContainer.getRootNode();

		if (getSelectedData() != null) {
			if (getSelectedData() == node) {
				return false;
			}
			getChart(null); // clear selection
			getSelectedData().setStyle("-fx-border-width: 2px; -fx-border-color: transparent;");
		}

		node.setStyle("-fx-border-width: 2px; -fx-border-color: lightblue;");
		setSelectedData(node);

		fileDataContainer.selectFile();

		return true;
	}

	public void scrollToChart(FileDataContainer fileDataContainer) {
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

	public void focusMapOnTrace(Trace trace) {
		if (trace == null) {
			return;
		}

		field.setSceneCenter(trace.getLatLon());
		eventPublisher.publishEvent(new WhatChanged(this, WhatChanged.Change.mapscroll));
	}

    public Collection<SensorLineChart> getCharts() {
		return csvFiles.values();
    }

	public void publishEvent(BaseEvent event) {
		eventPublisher.publishEvent(event);
	}

	public GPRChart getProfileFieldByPattern(@NotNull SgyFile f) {
		if (gprCharts.containsKey(f)) {
			return gprCharts.get(f);
		}

		//compare files by pattern


		var key = gprCharts.keySet().stream().filter(sgyFile -> sgyFile.getFile().getName()
				.contains(extractBaseGprFileName(f.getFile().getName()))).findAny().orElseGet(() -> f);
		var chart = gprCharts.get(key);
		if (chart != null) {
			chart.addSgyFile(f);
		} else {
			chart = new GPRChart(this, auxEditHandler, List.of(f));
		}
		gprCharts.put(f, chart);
		return chart;
	}

	private String extractBaseGprFileName(String fileName) {
		String gprFileNamePattern =  prefSettings.getSetting("general", "gpr_file_name_pattern");
		if (gprFileNamePattern == null) {
			gprFileNamePattern = "^(\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-gpr.*)_\\d{3}.*\\.sgy$";
		}

		Pattern regex = Pattern.compile(gprFileNamePattern);
		Matcher matcher = regex.matcher(fileName);

		if (matcher.find()) {
			return matcher.group(1);
		} else {
			return fileName;
		}
	}

	@EventListener
	private void onChange(WhatChanged event) {
		if (event.isJustdraw()) {
			csvFiles.values().forEach(v ->
					Platform.runLater(v::updateChartName)
			);
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

	// charts

	public List<Chart> getAllFileCharts() {
		List<Chart> charts = new ArrayList<>();
		charts.addAll(csvFiles.values());
		charts.addAll(gprCharts.values());
		return charts;
	}

	public Chart getFileChart(SgyFile file) {
		if (file instanceof CsvFile csvFile) {
			return getChart(csvFile).orElse(null);
		}
		if (file instanceof GprFile gprFile) {
			return getProfileField(gprFile);
		}
		return null;
	}

	// trace selection

	public List<ClickPlace> getSelectedTraces() {
		return Collections.unmodifiableList(selectedTraces);
	}

	public ClickPlace getSelectedTrace(Chart chart) {
		if (chart == null) {
			return null;
		}
		for (ClickPlace clickPlace : selectedTraces) {
			SgyFile traceFile = clickPlace.getTrace().getFile();
			if (Objects.equals(chart, getFileChart(traceFile))) {
				return clickPlace;
			}
		}
		return null;
	}

	public ClickPlace getSelectedTraceInCurrentChart() {
		Chart chart = getFileChart(currentFile);
		return chart != null
				? getSelectedTrace(chart)
				: null;
	}

	public void selectNearestTrace(LatLon location) {
		if (location == null) {
			return;
		}
		Trace nearestTrace = TraceUtils.findNearestTrace(getTraces(), location);
		selectTrace(nearestTrace, true);
	}

	public void selectTrace(Trace trace) {
		selectTrace(trace, false);
	}

	public void selectTrace(Trace trace, boolean focusOnChart) {
		if (trace == null) {
			return;
		}

		selectedTraces.clear();
		selectedTraces.add(toClickPlace(trace, true));

		Chart traceChart = getFileChart(trace.getFile());
		for (Chart chart : getAllFileCharts()) {
			if (Objects.equals(chart, traceChart)) {
				continue;
			}
			Trace nearestInChart = TraceUtils.findNearestTraceInFiles(
					chart.getFiles(),
					trace.getLatLon(),
					traceLookupThreshold);
			if (nearestInChart != null) {
				selectedTraces.add(toClickPlace(nearestInChart, false));
			}
		}

		Platform.runLater(() -> {
			for (Chart chart : getAllFileCharts()) {
				boolean focusOnTrace = focusOnChart || !Objects.equals(chart, traceChart);
				updateSelectedTraceOnChart(chart, focusOnTrace);
			}
			if (focusOnChart) {
				scrollToChart(traceChart);
			}
		});
	}

	private ClickPlace toClickPlace(Trace trace, boolean selected) {
		ClickPlace clickPlace = new ClickPlace(trace.getFile(), trace);
		clickPlace.setSelected(selected);
		return clickPlace;
	}

	public void clearSelectedTrace(Chart chart) {
		selectedTraces.removeIf(x -> Objects.equals(chart, getFileChart(x.getTrace().getFile())));
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

	private void updateSelectedTraceOnChart(Chart chart, boolean focusOnTrace) {
		if (chart == null) {
			return;
		}
		ClickPlace clickPlace = getSelectedTrace(chart);
		Trace trace = clickPlace != null
				? clickPlace.getTrace()
				: null;
		chart.selectTrace(trace, focusOnTrace);
	}

	// flags

	public void createFlagOnSelection(Chart chart) {
		if (chart == null) {
			return;
		}

		ClickPlace selectedTrace = getSelectedTrace(chart);
		if (selectedTrace == null) {
			return; // no trace selected in a chart
		}
		FoundPlace flag = toFlag(selectedTrace);
		if (flag == null) {
			return;
		}
		flag.setSelected(true);

		// clear current selection in file
		chart.selectFlag(null);
		chart.addFlag(flag);

		SgyFile traceFile = selectedTrace.getTrace().getFile();
		traceFile.getAuxElements().add(flag);
		traceFile.setUnsaved(true);

		clearSelectedTrace(chart);

		updateAuxElements();
		publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
	}

	private FoundPlace toFlag(ClickPlace selectedTrace) {
		if (selectedTrace == null) {
			return null;
		}

		Trace trace = selectedTrace.getTrace();
		SgyFile traceFile = trace.getFile();
		int traceIndex = traceFile instanceof CsvFile
				? trace.getIndexInFile()
				: trace.getIndexInSet();

		int localTraceIndex = traceFile.getOffset().globalToLocal(traceIndex);
		if (localTraceIndex < 0 || localTraceIndex >= traceFile.getTraces().size()) {
			log.warn("Flag outside of the current file bounds");
			return null;
		}

		return new FoundPlace(trace, traceFile.getOffset(), this);
	}

	public void removeSelectedFlag(Chart chart) {
		if (chart == null) {
			return;
		}

		List<FoundPlace> flags = chart.getFlags();
		FoundPlace selectedFlag = flags.stream()
				.filter(FoundPlace::isSelected)
				.findFirst()
				.orElse(null);
		if (selectedFlag == null) {
			return;
		}

		chart.removeFlag(selectedFlag);

		SgyFile traceFile = selectedFlag.getTrace().getFile();
		traceFile.getAuxElements().remove(selectedFlag);
		traceFile.setUnsaved(true);

		updateAuxElements();
		publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
	}

	public void removeAllFlags(Chart chart) {
		if (chart == null) {
			return;
		}

		chart.clearFlags();
		for (SgyFile file : chart.getFiles()) {
			boolean modified = file.getAuxElements().removeIf(
					x -> x instanceof FoundPlace);
			if (modified) {
				file.setUnsaved(true);
			}
		}
		updateAuxElements();
		publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
	}
}