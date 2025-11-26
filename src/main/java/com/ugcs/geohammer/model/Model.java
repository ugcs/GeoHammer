package com.ugcs.geohammer.model;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.ugcs.geohammer.AppContext;
import com.ugcs.geohammer.chart.Chart;
import com.ugcs.geohammer.chart.FileDataContainer;
import com.ugcs.geohammer.chart.csv.SensorLineChart;
import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.chart.gpr.GPRChart;
import com.ugcs.geohammer.format.svlog.SonarFile;
import com.ugcs.geohammer.model.element.AuxElementEditHandler;
import com.ugcs.geohammer.model.element.BaseObject;
import com.ugcs.geohammer.model.element.FoundPlace;
import com.ugcs.geohammer.model.event.FileClosedEvent;
import com.ugcs.geohammer.model.event.BaseEvent;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
import com.ugcs.geohammer.model.event.TemplateUnitChangedEvent;
import com.ugcs.geohammer.model.event.WhatChanged;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.util.Templates;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Traces;
import javafx.scene.layout.*;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ugcs.geohammer.math.MinMaxAvg;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ScrollPane;

@Component
public class Model implements InitializingBean {

	private static final Logger log = LoggerFactory.getLogger(Model.class);

	public static final int TOP_MARGIN = 60;

	@SuppressWarnings("NullAway.Init")
	@Value("${trace.lookup.threshold}")
	private Double traceLookupThreshold;

	private boolean loading = false;

	private final MapField field = new MapField();

	private final FileManager fileManager;

	private final List<BaseObject> auxElements = new ArrayList<>();

	private final List<TraceKey> selectedTraces = new ArrayList<>();

	private boolean kmlToFlagAvailable = false;

	private final AuxElementEditHandler auxEditHandler;

	private final VBox chartsContainer = new VBox(); // Charts container

    private final Map<SgyFile, Chart> charts = new HashMap<>();

    // TODO rename to csvCharts
	//private final Map<CsvFile, SensorLineChart> csvFiles = new HashMap<>();

	//private final Map<TraceFile, GPRChart> gprCharts = new HashMap<>();

	private final ApplicationEventPublisher eventPublisher;

	private final TemplateSettings templateSettings;

	@Nullable
	private Node selectedDataNode;

	@Nullable
	private SgyFile currentFile;

	public Model(FileManager fileManager, ApplicationEventPublisher eventPublisher, TemplateSettings templateSettings) {
		this.fileManager = fileManager;
		this.auxEditHandler = new AuxElementEditHandler(this);
		this.eventPublisher = eventPublisher;
		this.templateSettings = templateSettings;
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

	public TemplateSettings getTemplateSettings() {
		return templateSettings;
	}

	@Nullable
	public SgyFile getCurrentFile() {
		return currentFile;
	}

	public List<BaseObject> getAuxElements() {
        ArrayList<BaseObject> allElements = new ArrayList<>(auxElements);
        for (Chart chart : charts.values()) {
            // TODO don't use instanceof branching
            if (chart instanceof GPRChart gprChart) {
                allElements.addAll(gprChart.getAuxElements());
            }
        }
        return allElements;
	}

	public void updateAuxElements() {
        auxElements.clear();
        // TODO don't use instanceof branching
        for (Chart chart : charts.values()) {
            if (chart instanceof GPRChart gprChart) {
                gprChart.updateAuxElements();
            }
            if (chart instanceof SensorLineChart csvChart) {
                SgyFile file = csvChart.getFile();
                auxElements.addAll(file.getAuxElements());
                file.getAuxElements().forEach(element -> {
                    if (element instanceof FoundPlace foundPlace) {
                        chart.addFlag(foundPlace);
                    }
                });
            }
        }
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

			this.getMapField().adjustZoom(Chart.MIN_HEIGHT, 700);
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
			alert.initOwner(AppContext.stage);

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
	}

    // charts

    @Nullable
    public Chart getChart(@Nullable SgyFile file) {
        return charts.get(file);
    }

    @Nullable
    public GPRChart getGprChart(TraceFile file) {
        return charts.get(file) instanceof GPRChart gprChart ? gprChart : null;
    }

    public SensorLineChart getCsvChart(CsvFile file) {
        return charts.get(file) instanceof SensorLineChart csvChart ? csvChart : null;
    }

    public List<SensorLineChart> getSensorCharts() {
        return charts.values().stream()
                .filter(c -> c instanceof SensorLineChart)
                .map(c -> (SensorLineChart) c)
                .toList();
    }

    public String getSelectedSeriesName(@Nullable SgyFile file) {
        if (file == null) {
            return null;
        }
        if (getChart(file) instanceof SensorLineChart sensorChart) {
            return Strings.emptyToNull(sensorChart.getSelectedSeriesName());
        }
        return null;
    }

    // create chart

    private Chart newChart(SgyFile file) {
        if (file instanceof TraceFile traceFile) {
            return new GPRChart(this, traceFile);
        }
        if (file instanceof CsvFile csvFile) {
            return new SensorLineChart(this, csvFile);
        }
        if (file instanceof SonarFile sonarFile) {
            return new SensorLineChart(this, sonarFile);
        }
        return null;
    }

    private Chart createChart(SgyFile file) {
        // if there is a chart for a given file then put new chart
        // to the same position as it was before
        int index = -1;

        Chart chart = charts.get(file);
        if (chart != null) {
            Node chartBox = chart.getRootNode();
            if (chartBox != null) {
                index = chartsContainer.getChildren().indexOf(chartBox);
            }
        }

        chart = newChart(file);
        if (chart == null) {
            return null;
        }

        charts.remove(file);
        charts.put(file, chart);

        // create new chart contents
        var newChartBox = (VBox)chart.getRootNode();

        // add to container keeping position
        if (index != -1) {
            chartsContainer.getChildren().set(index, newChartBox);
        } else {
            chartsContainer.getChildren().add(newChartBox);
        }

        // adjust height
//        newChartBox.getChildren().forEach(node -> {
//            if (node instanceof StackPane) {
//                ((StackPane) node).setPrefHeight(Math.max(CHART_MIN_HEIGHT, node.getScene().getHeight()));
//                ((StackPane) node).setMinHeight(Math.max(CHART_MIN_HEIGHT, node.getScene().getHeight() / 2));
//            }
//        });
//        newChartBox.setPrefHeight(Math.max(400, newChartBox.getScene().getHeight()));
//        newChartBox.setMinHeight(Math.max(400, newChartBox.getScene().getHeight() / 2));

        chart.init();
        return chart;
    }

    public Chart initChart(SgyFile file) {
        Chart chart = charts.get(file);
        if (chart == null) {
            chart = createChart(file);
            fileManager.addFile(file);
        } else {
            // recreate chart
            fileManager.removeFile(file);
            chartsContainer.getChildren().remove(chart.getRootNode());
            charts.remove(file);

            chart = createChart(file);
            fileManager.addFile(file);
        }
        updateAuxElements();
        initField();

        selectAndScrollToChart(chart);
        return chart;
    }

    public void updateChartFile(SgyFile sgyFile, File file) {
        Chart chart = charts.remove(sgyFile);
        sgyFile.setFile(file);
        if (chart != null) {
            charts.put(sgyFile, chart);
        }
    }

    // close chart

    public void closeAllCharts() {
        charts.forEach((file, chart) -> {
            chart.close(false);
        });
        charts.clear();
    }

    public void removeChart(SgyFile sgyFile) {
        Chart removed = charts.remove(sgyFile);
        if (removed != null) {
            removed.getProfileScroll().setVisible(false);
            chartsContainer.getChildren().remove(removed.getRootNode());
        }

        // select first file in a list
        boolean chartSelected = false;
        List<SgyFile> openedFiles = fileManager.getFiles();
        if (!openedFiles.isEmpty()) {
            Chart chart = getChart(openedFiles.getFirst());
            if (chart != null) {
                selectAndScrollToChart(chart);
                chartSelected = true;
            }
        }
        if (!chartSelected) {
            publishEvent(new FileSelectedEvent(this, (SgyFile) null));
        }
    }

    // ----------------

	public void reloadChart(SgyFile file) {
		Chart chart = charts.get(file);
		if (chart != null) {
            chart.reload();
		}
	}

	public void chartsZoomOut() {
		charts.forEach((file, chart) -> {
			chart.zoomToFit();
		});
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

	@EventListener
	private void onChange(WhatChanged event) {
		if (event.isJustdraw()) {
			charts.values().forEach(chart ->
					Platform.runLater(chart::repaint)
			);
		}
	}

	@EventListener
	private void fileSelected(FileSelectedEvent event) {
		currentFile = event.getFile();
	}

	@EventListener
	private void fileClosed(FileClosedEvent event) {
		Chart chart = getChart(event.getFile());
		clearSelectedTrace(chart);
	}

	@EventListener
	public void onTemplateUnitChanged(TemplateUnitChangedEvent event) {
		SgyFile file = event.getFile();
        TraceUnit unit = event.getUnit();
        if (file == null || unit == null) {
            return;
        }

		String templateName = Templates.getTemplateName(file);
        for (Chart chart : charts.values()) {
            SgyFile chartFile = chart.getFile();
            if (!Objects.equals(file, chartFile)
                    && Objects.equals(templateName, Templates.getTemplateName(chartFile))) {
                chart.setTraceUnit(unit);
            }
        }
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
			if (Objects.equals(chart, getChart(trace.getFile()))) {
				return trace;
			}
		}
		return null;
	}

	@Nullable
	public TraceKey getSelectedTraceInCurrentChart() {
		Chart chart = getChart(currentFile);
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

		Chart traceChart = getChart(trace.getFile());
		boolean traceOnSelectedChart = isChartSelected(traceChart);

		for (Chart chart : charts.values()) {
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
			for (Chart chart : charts.values()) {
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
		selectedTraces.removeIf(x -> Objects.equals(chart, getChart(x.getFile())));
		Platform.runLater(() -> updateSelectedTraceOnChart(chart, false));
	}

	public void clearSelectedTraces() {
		selectedTraces.clear();
		Platform.runLater(() -> {
			for (Chart chart : charts.values()) {
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
