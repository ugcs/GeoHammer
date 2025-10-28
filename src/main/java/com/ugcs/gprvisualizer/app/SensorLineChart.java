package com.ugcs.gprvisualizer.app;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.github.thecoldwine.sigrun.common.ext.*;
import com.google.common.base.Strings;
import com.ugcs.gprvisualizer.app.auxcontrol.FoundPlace;
import com.ugcs.gprvisualizer.app.axis.SensorLineChartXAxis;
import com.ugcs.gprvisualizer.app.axis.SensorLineChartYAxis;
import com.ugcs.gprvisualizer.app.events.FileClosedEvent;
import com.ugcs.gprvisualizer.app.filter.MedianCorrectionFilter;
import com.ugcs.gprvisualizer.app.parsers.Semantic;
import com.ugcs.gprvisualizer.app.service.TemplateSettingsModel;
import com.ugcs.gprvisualizer.app.yaml.DataMapping;
import com.ugcs.gprvisualizer.app.yaml.data.SensorData;
import com.ugcs.gprvisualizer.event.FileSelectedEvent;
import com.ugcs.gprvisualizer.event.WhatChanged;
import com.ugcs.gprvisualizer.utils.Check;
import com.ugcs.gprvisualizer.utils.Nodes;
import com.ugcs.gprvisualizer.utils.Range;
import com.ugcs.gprvisualizer.utils.Views;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Side;
import javafx.scene.Cursor;
import javafx.scene.chart.ValueAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;

import javafx.scene.layout.*;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeType;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import com.ugcs.gprvisualizer.app.fir.FIRFilter;
import com.ugcs.gprvisualizer.app.parsers.GeoData;
import com.ugcs.gprvisualizer.app.parsers.SensorValue;
import com.ugcs.gprvisualizer.gpr.Model;

import javafx.beans.InvalidationListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.Node;
import javafx.scene.chart.Axis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart.Data;
import javafx.scene.chart.XYChart.Series;

public class SensorLineChart extends Chart {

    private static final double ZOOM_STEP = 1.38;

    private static final String FILTERED_SERIES_SUFFIX = "_filtered";

    private static final Logger log = LoggerFactory.getLogger(SensorLineChart.class);

    private final ApplicationEventPublisher eventPublisher;
    private final TemplateSettingsModel templateSettingsModel;
    private ValueAxis<Number> xAxis;
    @Nullable
    private LineChartWithMarkers lastLineChart = null;
    private Map<String, LineChartWithMarkers> charts = new HashMap<>();
    private Rectangle selectionRect = new Rectangle();
    private Label chartName;
    @Nullable
    private String selectedSeriesName;

    private Data<Number, Number> selectionMarker = null;
    private VBox root;
    private StackPane chartsContainer;
    private CsvFile file;
    private Map<String, Double> seriesMinValues = new HashMap<>();
    private Map<String, Double> seriesMaxValues = new HashMap<>();
    private Map<FoundPlace, Data<Number, Number>> foundPlaces = new HashMap<>();

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private ScheduledFuture<?> updateTask;

    static {
        Runtime.getRuntime().addShutdownHook(
                new Thread(scheduler::shutdownNow));
    }

    public SensorLineChart(Model model, ApplicationEventPublisher eventPublisher, TemplateSettingsModel templateSettingsModel) {
        super(model);
        this.eventPublisher = eventPublisher;
        this.templateSettingsModel = templateSettingsModel;
    }

    private EventHandler<MouseEvent> mouseClickHandler = new EventHandler<>() {
        @Override
        public void handle(MouseEvent event) {
        	if (event.getClickCount() == 2) {
                if (event.getSource() instanceof LineChart lineChart) {
                    Axis<Number> xAxis = lineChart.getXAxis();
                    Point2D point = xAxis.screenToLocal(event.getScreenX(), event.getScreenY());
                    Number xValue = xAxis.getValueForDisplay(point.getX());

                    int traceIndex = xValue.intValue();
                    if (traceIndex >= 0 && traceIndex < file.numTraces()) {
                        TraceKey trace = new TraceKey(file, traceIndex);
                        model.selectTrace(trace);
                        model.focusMapOnTrace(trace);
                    }
                }
                event.consume();
        	}
        }
	};

    public List<PlotData> generatePlotData(CsvFile csvFile) {
        // header -> unit
        Map<String, String> headers = new LinkedHashMap<>();
        String markHeader = GeoData.getHeader(Semantic.MARK, csvFile.getTemplate());
        for (GeoData data : csvFile.getGeoData()) {
            for (SensorValue value : data.getSensorValues()) {
                String header = value.header();
                if (Objects.equals(header, markHeader)) {
                    continue; // skip mark series
                }
                headers.putIfAbsent(header, Strings.nullToEmpty(value.unit()));
            }
        }

        List<PlotData> plotDataList = new ArrayList<>(headers.size());
        for (Map.Entry<String, String> e : headers.entrySet()) {
            String header = e.getKey();
            String unit = e.getValue();

            List<Number> headerValues = new ArrayList<>(csvFile.getGeoData().size());
            for (GeoData data : csvFile.getGeoData()) {
                SensorValue sensorValue = data.getSensorValue(header);
                headerValues.add(sensorValue != null ? sensorValue.data() : null);
            }

            PlotData plotData = new PlotData(
                    header,
                    unit,
                    getColor(header),
                    headerValues);
            plotDataList.add(plotData);
        }
        return plotDataList;
    }

    private Color getColor(String header) {
        return model.getColorByHeader(header);
    }

    @Override
    public Node getRootNode() {
        return root;
    }

    @Override
    public void selectFile() {
        eventPublisher.publishEvent(new FileSelectedEvent(this, file));
    }

    public record PlotData(String header, String unit, Color color, List<Number> data,
                           Set<Number> renderedIndices, boolean rendered) {

        public PlotData(String header, String unit, Color color, List<Number> data) {
            this(header, unit, color, data, new HashSet<>(), false);
        }

        public PlotData withData(List<Number> data) {
            return new PlotData(header, unit, color, data);
        }

        public PlotData render() {
            return new PlotData(header, unit, color, data, renderedIndices, true);
        }

        public String getPlotStyle() {
            return "-fx-stroke: " + Views.toColorString(color) + ";" + "-fx-stroke-width: 0.6px;";
        }

        public void setRendered(List<Data<Number, Number>> values) {
            if (values == null)
                return;
            values.forEach(v -> renderedIndices.add(v.getXValue()));
        }
    }

    public VBox createChartWithMultipleYAxes(CsvFile file, List<PlotData> plotDataList) {

        this.file = file;

        // Using StackPane to overlay charts
        this.chartsContainer = new StackPane();

        xAxis = createXAxis();

        for (PlotData plotData : plotDataList) {
            ValueAxis<Number> yAxis = createYAxis(plotData);
            createLineChart(plotData, xAxis, yAxis);
        }

        lastLineChart = charts.get(GeoData.getHeaderInFile(Semantic.LINE, file));

        if (lastLineChart != null) {
            lastLineChart.setMouseTransparent(false);

            setSelectionHandlers(lastLineChart);
            setScrollHandlers(lastLineChart);

            lastLineChart.addEventHandler(MouseEvent.MOUSE_CLICKED, mouseClickHandler);

            initLineMarkers();
        }

        initFlags();

        ImageView close = ResourceImageHolder.getImageView("close.png");
        close.setPickOnBounds(true);
        close.addEventHandler(MouseEvent.MOUSE_ENTERED, event -> {
            close.setCursor(Cursor.HAND);
        });
        close.setOnMouseClicked(event -> {
            event.consume();
            close();
        });

        chartName = new Label();
        chartName.setFont(Font.font("Verdana", FontWeight.BOLD, 8));
        chartName.setTextFill(Color.rgb(60, 60, 60));
        updateChartName();

        Region space = new Region();
        HBox.setHgrow(space, Priority.ALWAYS);

        HBox top = new HBox(close, chartName, space);
        top.setPadding(new Insets(0, 0, 0, 16));
        top.setSpacing(10);
        top.setAlignment(Pos.CENTER_LEFT);

        selectionRect.setManaged(false);
        selectionRect.setFill(null);
        selectionRect.setStroke(Color.BLUE);
        selectionRect.setMouseTransparent(true);

        root = new VBox(top, chartsContainer, selectionRect);
        root.setFillWidth(true);
        root.setSpacing(10);
        root.setAlignment(Pos.CENTER_RIGHT);
        root.setPadding(new Insets(10));

        root.setStyle("-fx-border-width: 2px; -fx-border-color: transparent;");
        root.setOnMouseClicked(event -> {
            if (model.selectChart(this)) {
                //eventPublisher.publishEvent(new FileSelectedEvent(file));
            }
        });

        getProfileScroll().setChangeListener((observable, oldVal, newVal) -> {
            zoomToProfileScroll();
            updateOnZoom(true);
        });
        updateProfileScroll();

        return root;
    }

    private void initLineMarkers() {
        Color lineColor = getColor(GeoData.getHeader(Semantic.LINE, file.getTemplate()));
        for (Map.Entry<Integer, Range> e : file.getLineRanges().entrySet()) {
            int lineIndex = e.getKey();
            Range lineRange = e.getValue();

            Data<Number, Number> verticalMarker = new Data<>(lineRange.getMin().intValue(), 0);

            Line line = new Line();
            line.setStroke(lineColor);
            line.setStrokeWidth(0.8);

            Pane icon = createRemoveLineIcon(lineIndex);

            lastLineChart.addVerticalValueMarker(
                    verticalMarker,
                    line,
                    null,
                    icon,
                    false);
        }
    }

    private void initFlags() {
        file.getAuxElements().stream().map(o -> ((FoundPlace) o))
                .forEach(this::putFoundPlace);
    }

    private Pane createRemoveLineIcon(int lineIndex) {
        Tooltip tooltip = new Tooltip("Remove Line " + lineIndex);
        ImageView imageView = ResourceImageHolder.getImageView("closeFile.png");
        Tooltip.install(imageView, tooltip);

        imageView.setPickOnBounds(true);
        imageView.addEventHandler(MouseEvent.MOUSE_ENTERED, event -> {
            imageView.setCursor(Cursor.HAND);
        });
        imageView.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            removeLine(lineIndex);
        });

        imageView.setTranslateX(1);
        imageView.setTranslateY(1);

        return new Pane(imageView);
    }

    private void removeLine(int lineIndex) {
        // if target line is last in a file, close the file
        if (file.getLineRanges().size() == 1) {
            close();
        } else {
            TraceTransform traceTransform = AppContext.getInstance(TraceTransform.class);
            traceTransform.removeLine(file, lineIndex);
        }
    }

    private @Nullable LineChartWithMarkers createLineChart(PlotData plotData, ValueAxis<Number> xAxis, ValueAxis<Number> yAxis) {
        var data = plotData.data();
        if (data.isEmpty()) {
            return null;
        }

        Series<Number, Number> series = new Series<>();
        series.setName(plotData.header());

        // Add data to chart
        addSeriesDataFiltered(series, plotData, 0, data.size());
        if (series.getData().isEmpty()) {
            return null;
        }

        Series<Number, Number> filtered = new Series<>();
        filtered.setName(plotData.header() + FILTERED_SERIES_SUFFIX);

        // Creating chart
        Range valueRange = getValueRange(data, plotData.header);
        ZoomRect outZoomRect = new ZoomRect(0, Math.max(0, data.size() - 1),
                valueRange.getMin().doubleValue(), valueRange.getMax().doubleValue());
        LineChartWithMarkers chart = new LineChartWithMarkers(xAxis, yAxis, outZoomRect, plotData);
        chart.resetZoomRect();

        chart.setLegendVisible(false); // Hide legend
        chart.setCreateSymbols(false); // Disable symbols
        chart.setMouseTransparent(true);
        chart.lookup(".chart-plot-background").setStyle("-fx-background-color: transparent;");

        // Set random color for series
        chart.getData().add(series);
        setStyleForSeries(series, plotData.getPlotStyle());

        chart.getData().add(filtered);
        setStyleForSeries(filtered, plotData.getPlotStyle());

        showChartAxes(chart, false);
        showChart(chart, false);

        // Add chart to container
        chartsContainer.getChildren().add(chart);
        charts.put(series.getName(), chart);

        return chart;
    }

    private ValueAxis<Number> createXAxis() {
        SensorLineChartXAxis xAxis = new SensorLineChartXAxis(model, templateSettingsModel, file, 10);
        xAxis.setSide(Side.BOTTOM);
        xAxis.setPrefHeight(50);
        xAxis.setMinorTickVisible(false);

        double lowerBound = 0;
        double upperBound = file.getGeoData().size() - 1;

        xAxis.setLowerBound(lowerBound);
        xAxis.setUpperBound(upperBound);

        return xAxis;
    }

    private ValueAxis<Number> createYAxis(PlotData plotData) {
        SensorLineChartYAxis yAxis = new SensorLineChartYAxis(10);
        yAxis.setLabel(plotData.unit());
        yAxis.setSide(Side.RIGHT); // Y-axis on the right
        yAxis.setPrefWidth(70);
        yAxis.setMinorTickVisible(false);

        Range valueRange = getValueRange(plotData.data, plotData.header);
        yAxis.setLowerBound(valueRange.getMin().doubleValue());
        yAxis.setUpperBound(valueRange.getMax().doubleValue());

        return yAxis;
    }

    @Override
    public void reload() {
        // clear selection
        clearSelectionMarker();
        // clear flags and markers
        clearFlags();
        if (lastLineChart != null) {
            lastLineChart.clearVerticalValueMarkers();
        }

        List<PlotData> plotDataList = generatePlotData(file);
        for (PlotData plotData : plotDataList) {
            LineChartWithMarkers lineChart = charts.get(plotData.header());
            if (lineChart == null) {
				ValueAxis<Number> yAxis = createYAxis(plotData);
				lineChart = createLineChart(plotData, xAxis, yAxis);

				if (lineChart == null) {
					continue;
				}
            }

            Range valueRange = getValueRange(plotData.data, plotData.header);

            lineChart.outZoomRect = new ZoomRect(
                    0, Math.max(0, plotData.data.size() - 1),
                    valueRange.getMin().doubleValue(), valueRange.getMax().doubleValue());
            lineChart.plotData = plotData;
            lineChart.filteredData = null;
            // update zoom
            lineChart.setZoomRect(lineChart.zoomRect);
        }

		// remove charts for headers that are no longer present
		Set<String> currentHeaders = plotDataList.stream()
				.map(PlotData::header)
				.collect(Collectors.toSet());

		for (Iterator<Map.Entry<String, LineChartWithMarkers>> it = charts.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<String, LineChartWithMarkers> entry = it.next();
			if (!currentHeaders.contains(entry.getKey())) {
				LineChartWithMarkers chart = entry.getValue();
				it.remove();
				chartsContainer.getChildren().remove(chart);
			}
		}

        // put new line markers and reposition flags
        initLineMarkers();
        initFlags();

        // restore selection
        TraceKey selectedTrace = model.getSelectedTrace(this);
        if (selectedTrace != null) {
            selectTrace(selectedTrace, false);
        }

        Platform.runLater(this::updateChartData);
    }

    public void updateChartName() {
        String fileName = (file.isUnsaved() ? "*" : "") + file.getFile().getName();
        chartName.setText(fileName);
    }

    public void updateXAxisUnits(TraceUnit traceUnit) {
        if (xAxis instanceof SensorLineChartXAxis axisWithUnits) {
            Platform.runLater(() -> axisWithUnits.setUnit(traceUnit));
        }
    }

    private void putFoundPlace(FoundPlace fp) {
        Data<Number, Number> verticalMarker = new Data<>(fp.getTraceIndex(), 0);

        var color = javafx.scene.paint.Color.rgb(
            fp.getFlagColor().getRed(),
            fp.getFlagColor().getGreen(),
            fp.getFlagColor().getBlue(),
            fp.getFlagColor().getAlpha() / 255.0
        );
        Line line = new Line();
        line.setStroke(color);
        line.setStrokeWidth(0.8);
        line.setTranslateY(46);

        var flagMarker = createFlagMarker(color);
        flagMarker.setTranslateX(0);
        flagMarker.setTranslateY(28);

        flagMarker.addEventHandler(MouseEvent.MOUSE_ENTERED, event -> {
            flagMarker.setCursor(Cursor.HAND);
        });

        flagMarker.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            model.selectChart(this);
            selectFlag(fp);
            fp.mousePressHandle(new Point2D(event.getScreenX(), event.getScreenY()), this);
            event.consume();
        });

        flagMarker.addEventHandler(MouseEvent.MOUSE_RELEASED, event -> {
            event.consume();
        });

        foundPlaces.put(fp, verticalMarker);
        lastLineChart.addVerticalValueMarker(verticalMarker, line, null, flagMarker, false);
    }

    public Range getVisibleXRange() {
        String seriesName = getSelectedSeriesName();
        LineChartWithMarkers selectedChart = charts.get(seriesName);
        if (selectedChart == null) {
            return new Range(0, 0);
        }
        ZoomRect zoomRect = selectedChart.zoomRect;
        return new Range(zoomRect.xMin, zoomRect.xMax);
    }

    @Override
    public int getVisibleNumberOfTrace() {
        String seriesName = getSelectedSeriesName();
        LineChartWithMarkers selectedChart = charts.get(seriesName);
        if (selectedChart == null) {
            return 0;
        }
        ZoomRect zoomRect = selectedChart.zoomRect;
        return zoomRect.xMax.intValue() - zoomRect.xMin.intValue() + 1;
    }

    @Override
    public int getTracesCount() {
        String seriesName = getSelectedSeriesName();
        LineChartWithMarkers selectedChart = charts.get(seriesName);
        if (selectedChart == null) {
            return 0;
        }
        ZoomRect zoomRect = selectedChart.outZoomRect;
        return zoomRect.xMax.intValue() - zoomRect.xMin.intValue() + 1;
    }

    private List<Data<Number, Number>> getSubsampleInRange(List<Number> data, int lowerIndex, int upperIndex,
            Set<Number> filter) {
        // Validate indices
        if (lowerIndex < 0 || upperIndex > data.size()) {
            throw new IllegalArgumentException("Invalid range specified.");
        }

        int numValues = 0;
        for (int i = lowerIndex; i < upperIndex; i++) {
            if (data.get(i) != null) {
                numValues++;
            }
        }
        if (numValues == 0) {
            return List.of();
        }

        // Calculate the number of points to sample within the specified range
        int limit = Math.min(numValues, 2000);
        int step = Math.max(1, numValues / limit);

        List<Data<Number, Number>> subsample = new ArrayList<>(limit);
        int k = 0; // step counter
        for (int i = lowerIndex; i < upperIndex; i++) {
            Number key = i;
            if (filter != null && filter.contains(key)) {
                continue;
            }
            Number value = data.get(i);
            if (value != null) {
                if (k == 0) {
                    subsample.add(new Data<>(key, value));
                }
                k = (k + 1) % step;
            }
        }

        return subsample;
    }

    private void addSeriesDataFiltered(XYChart.Series<Number, Number> series,
                                       PlotData plotData, int lowerIndex, int upperIndex) {
        List<Data<Number, Number>> subsample = getSubsampleInRange(
                plotData.data, lowerIndex, upperIndex, plotData.renderedIndices);

        if (subsample.isEmpty()) {
            if (series.getData().isEmpty()) {
                series.getData().add(new Data<>(0, 0));
            }
        } else {
            series.getData().addAll(subsample);
        }
        plotData.setRendered(subsample);
    }

    @Override
    public int getSelectedLineIndex() {
        if (selectionMarker != null) {
            int selectedIndex = selectionMarker.getXValue().intValue();
            if (isValueVisible(selectedIndex)) {
                return getValueLineIndex(selectedIndex);
            }
        }
        return getViewLineIndex();
    }

    private boolean isValueVisible(int index) {
        String seriesName = getSelectedSeriesName();
        LineChartWithMarkers selectedChart = charts.get(seriesName);
        if (selectedChart != null) {
            ValueAxis<Number> xAxis = (ValueAxis<Number>) selectedChart.getXAxis();
            return index >= xAxis.getLowerBound() && index <= xAxis.getUpperBound();
        }
        return false;
    }

    public int getValueLineIndex(int index) {
        List<GeoData> values = file.getGeoData();
        if (values == null || values.isEmpty()) {
            return 0;
        }
        // correct out of range values to point to the first or last trace
        index = Math.max(0, Math.min(index, values.size() - 1));
        String lineHeader = GeoData.getHeader(Semantic.LINE, file.getTemplate());
        return values.get(index).getInt(lineHeader).orElse(0);
    }

    public int getViewLineIndex() {
        String seriesName = getSelectedSeriesName();
        LineChartWithMarkers selectedChart = charts.get(seriesName);
        if (selectedChart != null) {
            ValueAxis<Number> xAxis = (ValueAxis<Number>) selectedChart.getXAxis();
            int xCenter = (int) (0.5 * (xAxis.getLowerBound() + xAxis.getUpperBound()));
            return getValueLineIndex(xCenter);
        }
        // default: first range key or 0
        SortedMap<Integer, Range> lineRanges = file.getLineRanges();
        return !lineRanges.isEmpty() ? lineRanges.firstKey() : 0;
    }

    private void updateProfileScroll() {
        String seriesName = getSelectedSeriesName();
        LineChartWithMarkers selectedChart = charts.get(seriesName);
        if (selectedChart == null) {
            return;
        }

        ZoomRect zoomRect = selectedChart.zoomRect;
        int xMin = zoomRect.xMin.intValue();
        int xMax = zoomRect.xMax.intValue();

        // num  visible traces
        int numTraces = xMax - xMin + 1;
        setMiddleTrace(xMin + numTraces / 2);

        // hScale = scroll width / num visible traces
        // aspect ratio = hScale / vScale
        double hScale = getProfileScroll().getWidth() / numTraces;
        double aspectRatio = hScale / getVScale();
        setRealAspect(aspectRatio);

        // update scroll view
        getProfileScroll().recalc();
    }

    private void zoomToProfileScroll() {
        // num visible traces = scroll width / hscale
        int numTraces = (int)(getProfileScroll().getWidth() / getHScale());
        int numTracesTotal = getTracesCount();

        int middle = getMiddleTrace();
        int w = numTraces / 2;

        // adjust visible range to the
        // full range bounds
        if (middle - w < 0) {
            middle = w;
        }
        if (middle + w >= numTracesTotal) {
            middle = numTracesTotal - w - 1;
        }

        Range range = new Range(middle - w, middle + w);
        for (LineChartWithMarkers chart : charts.values()) {
            ZoomRect zoomRect = chart.createZoomRectForXRange(range, true);
            chart.setZoomRect(zoomRect);
        }
    }

    private void zoomToLine(int lineIndex) {
        Range range = file.getLineRanges().get(lineIndex);
        for (LineChartWithMarkers chart : charts.values()) {
            ZoomRect zoomRect = chart.createZoomRectForXRange(range);
            chart.setZoomRect(zoomRect);
        }
    }

    @Override
    public void zoomToCurrentLine() {
        int lineIndex = getSelectedLineIndex();
        zoomToLine(lineIndex);
        updateOnZoom(false);
    }

    @Override
    public void zoomToPreviousLine() {
        int lineIndex = getViewLineIndex();
        SortedMap<Integer, Range> lineRanges = file.getLineRanges();
        int firstLineIndex = !lineRanges.isEmpty() ? lineRanges.firstKey() : 0;
        zoomToLine(Math.max(lineIndex - 1, firstLineIndex));
        updateOnZoom(false);
    }

    @Override
    public void zoomToNextLine() {
        int lineIndex = getViewLineIndex();
        SortedMap<Integer, Range> lineRanges = file.getLineRanges();
        int lastLineIndex = !lineRanges.isEmpty() ? lineRanges.lastKey() : 0;
        zoomToLine(Math.min(lineIndex + 1, lastLineIndex));
        updateOnZoom(false);
    }

    /**
     * Zoom to full range
     */
    @Override
    public void zoomToFit() {
        for (LineChartWithMarkers chart : charts.values()) {
            chart.resetZoomRect();
        }
        updateOnZoom(false);
    }

    @Override
    public void zoomIn() {
        double scale = 1.0 / ZOOM_STEP;
        zoom(scale, 1.0, null);
        updateOnZoom(false);
    }

    @Override
    public void zoomOut() {
        double scale = ZOOM_STEP;
        zoom(scale, 1.0, null);
        updateOnZoom(false);
    }

    private void zoomToArea(Point2D start, Point2D end) {
        for (LineChartWithMarkers chart : charts.values()) {
            ZoomRect zoomRect = chart.createZoomRectForArea(start, end);
            chart.setZoomRect(zoomRect);
        }
    }

    private void zoom(double scaleX, double scaleY, Point2D scaleCenter) {
        for (LineChartWithMarkers chart : charts.values()) {
            ZoomRect zoomRect = chart.scaleZoomRect(chart.zoomRect, scaleX, scaleY, scaleCenter);
            chart.setZoomRect(zoomRect);
        }
    }

    private void updateOnZoom(boolean delayed) {
        updateProfileScroll();
        model.publishEvent(new WhatChanged(this, WhatChanged.Change.csvDataZoom));

        if (delayed) {
            scheduleUpdate(() -> {
                Platform.runLater(this::updateChartData);
            }, 300);
        } else {
            Platform.runLater(this::updateChartData);
        }
    }

    private void updateChartData() {
        for (LineChartWithMarkers chart : charts.values()) {
            chart.updateLineChartData();
        }
    }

    /**
     * Close chart
     */
    private void close() {
        if (!confirmUnsavedChanges()) {
            return;
        }
        close(true);
    }

    /**
     * Close chart
     * @param removeFromModel
     */
    public void close(boolean removeFromModel) {
        if (root.getParent() instanceof VBox) {
            // hide profile scroll
            if (getProfileScroll() != null) {
                getProfileScroll().setVisible(false);
            }
            // remove charts
            VBox parent = (VBox) root.getParent();
            parent.getChildren().remove(root);

            if (removeFromModel) {
                // remove files and traces from map
                model.publishEvent(new FileClosedEvent(this, file));
            }
        }
    }

    private void setSelectionRectFromDragBounds(DragBounds bounds) {
        selectionRect.setX(bounds.getMinX());
        selectionRect.setY(bounds.getMinY());
        selectionRect.setWidth(bounds.getWidth());
        selectionRect.setHeight(bounds.getHeight());
    }

    private void setSelectionHandlers(LineChartWithMarkers chart) {
        // selection coordinates in a root coordinate space
        DragBounds dragBounds = new DragBounds(Point2D.ZERO, Point2D.ZERO);

        chart.setOnMousePressed(event -> {
            Bounds chartBounds = Nodes.getBoundsInParent(chart, root);
            Point2D pointInScene = new Point2D(
                    event.getX() + chartBounds.getMinX(),
                    event.getY() + chartBounds.getMinY());
            dragBounds.setStart(pointInScene);
            dragBounds.setStop(pointInScene);
            setSelectionRectFromDragBounds(dragBounds);
            selectionRect.setVisible(true);
        });

        chart.setOnMouseDragged(event -> {
            Bounds chartBounds = Nodes.getBoundsInParent(chart, root);
            Point2D pointInScene = new Point2D(
                    event.getX() + chartBounds.getMinX(),
                    event.getY() + chartBounds.getMinY());
            dragBounds.setStop(pointInScene);
            setSelectionRectFromDragBounds(dragBounds);
        });

        chart.setOnMouseReleased(event -> {
            selectionRect.setVisible(false);
            if (selectionRect.getWidth() < 10 || selectionRect.getHeight() < 10) {
                return;
            }

            // convert selection coordinates to chart coordinates
            Bounds chartBounds = Nodes.getBoundsInParent(chart, root);
            double startX = selectionRect.getX() - chartBounds.getMinX();
            double startY = selectionRect.getY() - chartBounds.getMinY();
            double endX = startX + selectionRect.getWidth();
            double endY = startY + selectionRect.getHeight();

            zoomToArea(new Point2D(startX, startY), new Point2D(endX, endY));
            updateOnZoom(false);
        });
    }

    private void setScrollHandlers(LineChartWithMarkers chart) {
        chart.setOnScroll((ScrollEvent event) -> {
            Point2D scaleCenter = new Point2D(event.getX(), event.getY());
            boolean scaleY = !event.isControlDown();
            double scaleFactor = 1.1;
            if (event.getDeltaY() > 0) {
                scaleFactor = 1 / scaleFactor;
            }

            zoom(scaleFactor, scaleY ? scaleFactor : 1, scaleCenter);
            updateOnZoom(true);

            event.consume(); // don't scroll parent pane
        });
    }

    private void scheduleUpdate(Runnable update, long delayMillis) {
        if (updateTask != null) {
            updateTask.cancel(false);
        }
        updateTask = scheduler.schedule(update, delayMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Remove vertical marker 
     */
    public void clearSelectionMarker() {
        if (lastLineChart != null && selectionMarker != null) {
            lastLineChart.removeVerticalValueMarker(selectionMarker);
            selectionMarker = null;
        }
    }

    /**
     * Add vertical marker
     * @param x
     */
    public void putSelectionMarker(int x) {
        if (lastLineChart != null) {
            if (selectionMarker != null) {
                lastLineChart.removeVerticalValueMarker(selectionMarker);
            }
            selectionMarker = new Data<>(x, 0);
            lastLineChart.addVerticalValueMarker(selectionMarker);
        }
    }

    public double getSeriesMinValue() {
        return seriesMinValues.getOrDefault(getSelectedSeriesName(), 0.0);
    }

    public double getSeriesMaxValue() {
        return seriesMaxValues.getOrDefault(getSelectedSeriesName(), 0.0);
    }

    private double getScaleFactor(double value) {
        int base = (int)Math.clamp(Math.floor(Math.log10(Math.abs(value))), 0, 3);
        return Math.pow(10, base);
    }

    private Range getValueRange(List<Number> data, String header) {
        Double min = null;
        Double max = null;
        for (Number value : data) {
            if (value == null) {
                continue;
            }
            double unboxed = value.doubleValue();
            if (min == null || unboxed < min) {
                min = unboxed;
            }
            if (max == null || unboxed > max) {
                max = unboxed;
            }
        }
        if (min == null) {
            min = 0.0;
        }
        if (max == null) {
            max = 0.0;
        }

        seriesMinValues.put(header, min);
        seriesMaxValues.put(header, max);

        // get scale factor and align min max to it
        double f = Math.max(getScaleFactor(min), getScaleFactor(max));
        double minAligned = f * Math.floor(min / f);
        double maxAligned = f * Math.ceil(max / f);
        if (Math.abs(maxAligned - minAligned) < 1e-6) {
            minAligned -= f;
            maxAligned += f;
        }

        return new Range(minAligned, maxAligned);
    }

    public void selectChart(@Nullable String seriesName) {
        if (Objects.equals(seriesName, selectedSeriesName)) {
            return;
        }
        // clear current selection
        LineChartWithMarkers selectedChart = charts.get(selectedSeriesName);
        showChartAxes(selectedChart, false);
        selectedSeriesName = null;
        // select chart
        if (seriesName != null) {
            LineChartWithMarkers chart = charts.get(seriesName);
            if (chart != null) {
                showChartAxes(chart, true);
                selectedSeriesName = seriesName;
            }
        }
        if (model.isChartSelected(this)) {
            selectFile();
        }
    }

    private void showChartAxes(@Nullable LineChartWithMarkers chart, boolean show) {
        if (chart == null) {
            return;
        }

        chart.setVerticalGridLinesVisible(show);
        chart.setHorizontalGridLinesVisible(show);
        chart.setHorizontalZeroLineVisible(show);
        chart.setVerticalZeroLineVisible(show);

        chart.getYAxis().setOpacity(show ? 1 : 0);
    }

    public void showChart(String seriesName, boolean show) {
        showChart(charts.get(seriesName), show);
    }

    public void showChart(@Nullable LineChartWithMarkers chart, boolean show) {
        if (chart == null) {
            return;
        }

        for (Series<Number, Number> series : chart.getData()) {
            series.getNode().setVisible(show);
        }
    }

    // Apply color to data series
    private void setStyleForSeries(Series<Number, Number> series, String plotStyle) {
        //String colorString = getColorString(color);
        //System.out.println("Color: " + colorString);
        // Apply style to series
        series.getNode().lookup(".chart-series-line").setStyle(plotStyle);
        //.setStyle("-fx-stroke: " + colorString + ";" + "-fx-stroke-width: 0.6px;");
    }

    private record ZoomRect(Number xMin, Number xMax, Number yMin, Number yMax) {}

    /**
     * Line chart with markers
     */
    private class LineChartWithMarkers extends LineChart<Number, Number> {

        private final ObservableList<Data<Number, Number>> horizontalMarkers;
        private final ObservableList<Data<Number, Number>> verticalMarkers;
        //private final List<Data<Number, Number>> buttonMarkers;
        //private final List<Data<Number, Number>> flagMarkers;

        private ZoomRect outZoomRect;
        private ZoomRect zoomRect;

        private PlotData plotData;
        private PlotData filteredData;

        private FilterOptions filterOptions = new FilterOptions();

        //private final List<Data<Number, Number>> subsampleInFullRange;

        public LineChartWithMarkers(Axis<Number> xAxis, Axis<Number> yAxis, ZoomRect outZoomRect, PlotData plotData) {
            super(xAxis, yAxis);

            this.outZoomRect = outZoomRect;

            horizontalMarkers = FXCollections.observableArrayList(data -> new Observable[] {data.YValueProperty()});
            horizontalMarkers.addListener((InvalidationListener)observable -> layoutPlotChildren());
            verticalMarkers = FXCollections.observableArrayList(data -> new Observable[] {data.XValueProperty()});
            verticalMarkers.addListener((InvalidationListener)observable -> layoutPlotChildren());
            //buttonMarkers = new ArrayList<>();// FXCollections.observableArrayList(data -> new Observable[] {data.XValueProperty()});
            //buttonMarkers.addListener((InvalidationListener) observable -> layoutPlotChildren());
            //flagMarkers = new ArrayList<>();

            this.plotData = plotData;
        }

        public void rescaleY() {
            Range oldYRange = new Range(outZoomRect.yMin, outZoomRect.yMax);
            Range newYRange = getValueRange(plotData.data, plotData.header);

            outZoomRect = new ZoomRect(
                    0, Math.max(0, plotData.data.size() - 1),
                    newYRange.getMin(), newYRange.getMax());

            if (zoomRect != null) {
                double widthRate = newYRange.getWidth() / oldYRange.getWidth();
                Number newYMin = newYRange.getMin().doubleValue()
                        + widthRate * (zoomRect.yMin.doubleValue() - oldYRange.getMin().doubleValue());
                Number newYMax = newYRange.getMin().doubleValue()
                        + widthRate * (zoomRect.yMax.doubleValue() - oldYRange.getMin().doubleValue());
                setZoomRect(new ZoomRect(zoomRect.xMin, zoomRect.xMax, newYMin, newYMax));
            }
        }

        public ZoomRect createZoomRectForArea(Point2D start, Point2D end) {
            // start and end are in chart coordinates
            Node plot = lookup(".chart-plot-background");
            Bounds plotBounds = Nodes.getBoundsInParent(plot, this);
            // translate coordinates to a plot space
            Point2D plotStart = new Point2D(
                    Math.clamp(start.getX() - plotBounds.getMinX(), 0, plotBounds.getWidth()),
                    Math.clamp(start.getY() - plotBounds.getMinY(), 0, plotBounds.getHeight()));
            Point2D plotEnd = new Point2D(
                    Math.clamp(end.getX() - plotBounds.getMinX(), 0, plotBounds.getWidth()),
                    Math.clamp(end.getY() - plotBounds.getMinY(), 0, plotBounds.getHeight()));

            ValueAxis<Number> xAxis = (ValueAxis<Number>)getXAxis();
            Number xMin = xAxis.getValueForDisplay(plotStart.getX());
            Number xMax = xAxis.getValueForDisplay(plotEnd.getX());

            ValueAxis<Number> yAxis = (ValueAxis<Number>)getYAxis();
            Number yMax = yAxis.getValueForDisplay(plotStart.getY());
            Number yMin = yAxis.getValueForDisplay(plotEnd.getY());

            return new ZoomRect(xMin, xMax, yMin, yMax);
        }

        public ZoomRect createZoomRectForXRange(Range range) {
            return createZoomRectForXRange(range, false);
        }

        public ZoomRect createZoomRectForXRange(Range range, boolean keepYScale) {
            if (range == null) {
                return outZoomRect;
            }
            ZoomRect yRect = keepYScale ? zoomRect : outZoomRect;
            return new ZoomRect(range.getMin(), range.getMax(), yRect.yMin, yRect.yMax);
        }

        public ZoomRect scaleZoomRect(ZoomRect zoomRect, double xScale, double yScale, Point2D scaleCenter) {
            if (zoomRect == null)
                return null;

            // scale center is in chart coordinates
            // translate chart coordinates to plot coordinates
            Node plot = lookup(".chart-plot-background");
            Bounds plotBounds = Nodes.getBoundsInParent(plot, this);
            Point2D plotScaleCenter = null;
            if (scaleCenter != null ) {
                plotScaleCenter = new Point2D(
                        scaleCenter.getX() - plotBounds.getMinX(),
                        scaleCenter.getY() - plotBounds.getMinY());
            }

            // convert center coordinates to x and y ratios [0, 1]
            // when scale center is null zoom at plot center
            double xCenterRatio = plotScaleCenter != null && plotBounds.getWidth() > 1e-9
                    ? Math.clamp(plotScaleCenter.getX() / plotBounds.getWidth(), 0, 1)
                    : 0.5;
            double yCenterRatio = plotScaleCenter != null && plotBounds.getHeight() > 1e-9
                    ? Math.clamp(1 - plotScaleCenter.getY() / plotBounds.getHeight(), 0, 1)
                    : 0.5;

            Range xRange = new Range(zoomRect.xMin, zoomRect.xMax)
                    .scale(xScale, xCenterRatio);
            Range yRange = new Range(zoomRect.yMin, zoomRect.yMax)
                    .scale(yScale, yCenterRatio);

            return new ZoomRect(
                    xRange.getMin(), xRange.getMax(),
                    yRange.getMin(), yRange.getMax());
        }

        public ZoomRect cropZoomRect(ZoomRect zoomRect, ZoomRect cropRect) {
            if (zoomRect == null || cropRect == null)
                return zoomRect;

            return new ZoomRect(
                    Math.max(zoomRect.xMin.doubleValue(), cropRect.xMin.doubleValue()),
                    Math.min(zoomRect.xMax.doubleValue(), cropRect.xMax.doubleValue()),
                    Math.max(zoomRect.yMin.doubleValue(), cropRect.yMin.doubleValue()),
                    Math.min(zoomRect.yMax.doubleValue(), cropRect.yMax.doubleValue()));
        }

        public void setZoomRect(ZoomRect zoomRect) {
            this.zoomRect = zoomRect != outZoomRect
                    ? cropZoomRect(zoomRect, outZoomRect)
                    : zoomRect;
            applyZoom();
        }

        public void resetZoomRect() {
            setZoomRect(outZoomRect);
        }

        private void applyZoom() {
            int lineIndexBefore = getSelectedLineIndex();

            ValueAxis<Number> xAxis = (ValueAxis<Number>)getXAxis();
            xAxis.setAutoRanging(false);
            xAxis.setLowerBound(zoomRect.xMin.doubleValue());
            xAxis.setUpperBound(zoomRect.xMax.doubleValue());

            ValueAxis<Number> yAxis = (ValueAxis<Number>) getYAxis();
            yAxis.setAutoRanging(false);
            yAxis.setLowerBound(zoomRect.yMin.doubleValue());
            yAxis.setUpperBound(zoomRect.yMax.doubleValue());

            int lineIndexAfter = getSelectedLineIndex();
            if (lineIndexBefore != lineIndexAfter) {
                // request redraw on selected line index update
                eventPublisher.publishEvent(new WhatChanged(
                        SensorLineChart.this,
                        WhatChanged.Change.justdraw
                ));
            }
        }

        private void updateLineChartData() {
            int lowerIndex = Math.clamp(zoomRect.xMin.intValue(), 0, plotData.data().size() - 1);
            int upperIndex = Math.clamp(zoomRect.xMax.intValue(), lowerIndex, plotData.data().size());

            getData().forEach(series -> {
                if (series.getName().contains(FILTERED_SERIES_SUFFIX)) {
                    if (filteredData == null || !filteredData.rendered) {
                        series.getData().clear();
                        if (filteredData != null) {
                            filteredData.renderedIndices.clear();
                        }
                        if (filteredData == null) {
                            series.getData().add(new Data<Number,Number>(0, 0));
                        }
                    } 
                    if (filteredData != null) {
                        addSeriesDataFiltered(series, filteredData, lowerIndex, upperIndex);

                        Node lineNode = series.getNode() != null ? series.getNode().lookup(".chart-series-line") : null;
                        if (lineNode != null) {
                            lineNode.setStyle(emphasizeStyle(plotData.getPlotStyle()));
                        }

                        if (!filteredData.rendered) {
                            filteredData = filteredData.render();
                        }
                    }
                } else {
                    var node = series.getNode().lookup(".chart-series-line");
                    node.setStyle(plotData.getPlotStyle());
                    if (filteredData != null) {
                            node.setStyle(node.getStyle() + "-fx-stroke-dash-array: 1 5 1 5;");
                    }
                    if (!plotData.rendered) {
                        series.getData().clear();
                        plotData.renderedIndices.clear();
                    }
                    addSeriesDataFiltered(series, plotData, lowerIndex, upperIndex);
                    if (!plotData.rendered) {
                        plotData = plotData.render();
                    }
                }
            });
        }

        private void clearLineChartData() {
            // TODO: better to recreate series because clear is too slow
            if (!plotData.data.isEmpty()) {
                getData().forEach(series -> {
                    //System.out.println("start clear: " + System.currentTimeMillis());
                    series.getData().clear();
                    //System.out.println("complete clear: " + System.currentTimeMillis());
                });
                plotData.renderedIndices.clear();
            }
        }

        /**
         * Add horizontal value marker.
         *
         * @param marker horizontal value marker.
         */
        public void addHorizontalValueMarker(Data<Number, Number> marker) {
            Objects.requireNonNull(marker, "the marker must not be null");
            if (horizontalMarkers.contains(marker)) return;
            Line line = new Line();
            marker.setNode(line );
            getPlotChildren().add(line);
            horizontalMarkers.add(marker);
        }

        /**
         * Remove horizontal value marker.
         *
         * @param marker horizontal value marker.
         */
        public void removeHorizontalValueMarker(Data<Number, Number> marker) {
            Objects.requireNonNull(marker, "the marker must not be null");
            if (marker.getNode() != null) {
                getPlotChildren().remove(marker.getNode());
                marker.setNode(null);
            }
            horizontalMarkers.remove(marker);
        }

        public void addVerticalValueMarker(Data<Number, Number> marker) {
            Line line = new Line();
            line.setStroke(Color.RED); // Bright color for white background
            line.setStrokeWidth(1);
            line.setTranslateY(15);

            ImageView imageView = ResourceImageHolder.getImageView("gps32.png");
            imageView.setTranslateY(17);

            addVerticalValueMarker(marker, line, imageView, null, true);
        }

        /**
         * Add a vertical value marker to the chart.
         *
         * @param marker    the data point to be marked
         * @param line      the line to be used for the marker
         * @param imageView the image to be displayed at the marker
         */
        public void addVerticalValueMarker(Data<Number, Number> marker, Line line, ImageView imageView, Pane flag, boolean mouseTransparent) {
            Objects.requireNonNull(marker, "the marker must not be null");
            if (verticalMarkers.contains(marker)) return;

            VBox markerBox = new VBox();
            markerBox.setAlignment(Pos.TOP_CENTER);
            markerBox.setMouseTransparent(mouseTransparent);

            VBox imageContainer = new VBox();
            imageContainer.setAlignment(Pos.TOP_CENTER);

            if (imageView != null) {
                imageContainer.getChildren().add(imageView);
            }

            if (flag != null) {
                imageContainer.getChildren().add(flag);
            }

            markerBox.getChildren().add(imageContainer);

            markerBox.getChildren().add(line);

            marker.setNode(markerBox);
            getPlotChildren().add(markerBox);

            verticalMarkers.add(marker);
        }

        /**
         * Remove vertical value marker.
         *
         * @param marker vertical value marker.
         */
        public void removeVerticalValueMarker(Data<Number, Number> marker) {
            Objects.requireNonNull(marker, "the marker must not be null");
            if (marker.getNode() != null) {
                getPlotChildren().remove(marker.getNode());
                marker.setNode(null);
            }
            verticalMarkers.remove(marker);
        }

        private void clearVerticalValueMarkers() {
            for (Data<Number, Number> marker : new ArrayList<>(verticalMarkers)) {
                removeVerticalValueMarker(marker);
            }
        }

        @Override
        protected void layoutPlotChildren() {
            super.layoutPlotChildren();

            for (Data<Number, Number> horizontalMarker : horizontalMarkers) {
                Line line = (Line) horizontalMarker.getNode();
                line.setStartX(0);
                line.setEndX(getBoundsInLocal().getWidth());
                line.setStartY(getYAxis().getDisplayPosition(horizontalMarker.getYValue()) + 0.5); // 0.5 for crispness
                line.setEndY(line.getStartY());
                line.toFront();
            }

            for (Data<Number, Number> verticalMarker : verticalMarkers) {
                VBox markerBox = (VBox) verticalMarker.getNode();
                markerBox.setLayoutX(getXAxis().getDisplayPosition(verticalMarker.getXValue()));

                VBox imageContainer = (VBox) markerBox.getChildren().get(0);
                double imageHeight = imageContainer.getChildren().stream()
                    .mapToDouble(node -> node.getBoundsInLocal().getHeight())
                    .sum();

                Line line = (Line) markerBox.getChildren().get(1);
                line.setStartY(imageHeight);
                line.setEndY(getBoundsInLocal().getHeight());

                markerBox.setLayoutY(0d);
                markerBox.setMinHeight(getBoundsInLocal().getHeight());
                markerBox.toFront();
            }
        }

        public FilterOptions getFilterOptions() {
            return filterOptions;
        }

        public void setFilterOptions(FilterOptions filterOptions) {
            Check.notNull(filterOptions);
            this.filterOptions = filterOptions;
        }
    }

    private record FilterOptions(int lowPassOrder, int timeLagShift) {

        public FilterOptions() {
            this(0, 0);
        }

        public FilterOptions withLowPassOrder(int lowPassOrder) {
            return new FilterOptions(lowPassOrder, this.timeLagShift);
        }

        public FilterOptions withTimeLagShift(int timeLagShift) {
            return new FilterOptions(this.lowPassOrder, timeLagShift);
        }

        public boolean hasAny() {
            return lowPassOrder != 0 || timeLagShift != 0;
        }
    }

    // Create flag marker
    private Pane createFlagMarker(Color color) {
        Line flagPole = new Line(0, 0, 0, 18);
        flagPole.setStroke(Color.BLACK);
        flagPole.setStrokeWidth(0.8);

        Polygon flag = new Polygon();
        flag.getPoints().addAll(0.0, 0.0, 
        15.0, 0.0, 
        10.0, 5.0, 
        15.0, 10.0, 
        0.0, 10.0);

        flag.setFill(color);
        flag.setStroke(Color.BLACK);
        flag.setStrokeWidth(0.8);

        Pane flagMarker = new Pane();
        flagMarker.getChildren().addAll(flagPole, flag);

        flag.setTranslateX(0);
        flag.setTranslateY(-10);

        return flagMarker;
    }

    public void gnssTimeLag(String seriesName, int timeLagShift) {
        LineChartWithMarkers chart = getDataChart(seriesName);
        if (chart == null) {
            return;
        }

        chart.setFilterOptions(chart.getFilterOptions().withTimeLagShift(timeLagShift));
        applyFilters(chart);

        Platform.runLater(chart::updateLineChartData);
    }

    public void lowPassFilter(String seriesName, int lowPassOrder) {
        LineChartWithMarkers chart = getDataChart(seriesName);
        if (chart == null) {
            return;
        }

        chart.setFilterOptions(chart.getFilterOptions().withLowPassOrder(lowPassOrder));
        applyFilters(chart);

        Platform.runLater(chart::updateLineChartData);
    }

    private void applyFilters(LineChartWithMarkers chart) {
        Check.notNull(chart);
        FilterOptions filterOptions = chart.getFilterOptions();

        if (!filterOptions.hasAny()) {
            // undo all
            chart.filteredData = null;
            for (int i = 0; i < file.getGeoData().size(); i++) {
                file.getGeoData().get(i).undoSensorValue(chart.plotData.header);
            }
            return;
        }

        List<Number> values = chart.plotData.data;
        List<Number> filtered = new ArrayList<>(values.size());
        for (Range range : file.getLineRanges().values()) {
            int from = range.getMin().intValue();
            int to = range.getMax().intValue();

            List<Number> rangeValues = values.subList(from, to + 1);
            // low-pass
            try {
                if (filterOptions.lowPassOrder() != 0) {
                    rangeValues = lowPass(rangeValues,
                            filterOptions.lowPassOrder());
                }
            } catch (Exception e) {
                log.error("Low-pass filter error", e);
            }
            // time lag
            try {
                if (filterOptions.timeLagShift() != 0) {
                    rangeValues = gnssTimeLag(rangeValues,
                            filterOptions.timeLagShift());
                }
            } catch (Exception e) {
                log.error("Time lag filter error", e);
            }
            // put all back
            filtered.addAll(rangeValues);
        }

        chart.filteredData = chart.plotData.withData(filtered);
        for (int i = 0; i < file.getGeoData().size(); i++) {
            file.getGeoData().get(i).setSensorValue(chart.plotData.header, filtered.get(i));
        }
        file.setUnsaved(true);
        Platform.runLater(this::updateChartName);
    }

    private List<Number> gnssTimeLag(List<Number> values, int shift) {
        if (values == null || values.isEmpty()) {
            return values;
        }

        Number[] filtered = new Number[values.size()];

        int l = 0;
        int r = values.size() - 1;

        // shift > 0 -> move left
        if (shift > 0) {
            l += shift;
        } else {
            // shift is negative
            r += shift;
        }
        for (int i = l; i <= r; i++) {
            int j = i - shift;
            if (j >= 0 && j < filtered.length) {
                filtered[j] = values.get(i);
            }
        }
        return Arrays.asList(filtered);
    }

    private List<Number> lowPass(List<Number> values, int order) {
        if (values == null || values.isEmpty()) {
            return values;
        }

        // data size should be >= 2 * filterOrder + 1
        // min filter order = (size - 1) / 2
        order = Math.min(order, (values.size() - 1) / 2);
        int shift = order / 2;
        FIRFilter filter = new FIRFilter(order);

        var filtered = filter.filterList(values).subList(shift, values.size() + shift);
        assert filtered.size() == values.size();

        List<Number> valuesNonNull = new ArrayList<>();
        List<Number> filteredValuesNonNull = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            Number value = values.get(i);
            Number filteredValue = filtered.get(i);
            if (value != null && filteredValue != null) {
                valuesNonNull.add(value);
                filteredValuesNonNull.add(filteredValue);
            }
        }

        double rmsOriginal = calculateRMS(valuesNonNull);
        double rmsFiltered = calculateRMS(filteredValuesNonNull);

        double scale = rmsOriginal != 0.0 && rmsFiltered != 0.0
                ? rmsOriginal / rmsFiltered
                : 0.0;
        for (int i = 0; i < filtered.size(); i++) {
            Number value = filtered.get(i);
            if (value != null) {
                filtered.set(i, scale * value.doubleValue());
            }
        }
        return filtered;
    }

    private static double calculateRMS(List<Number> values) {
        double sum = 0.0;
        int count = 0;
        for (Number value : values) {
            if (value == null) {
                continue;
            }
            sum += value.doubleValue() * value.doubleValue();
            count++;
        }
        return count > 0 ? Math.sqrt(sum / count) : 0.0;
    }

    public void medianCorrection(String seriesName, int window) {
        if (Strings.isNullOrEmpty(seriesName)) {
            return;
        }

        DataMapping mapping = file.getTemplate().getDataMapping();
        String semantic = mapping.getSemanticByHeader(seriesName);

        // if selected column is a semantic column, use origin
        if (!Strings.isNullOrEmpty(semantic) && semantic.endsWith(Semantic.ANOMALY_SUFFIX)) {
            semantic = semantic.substring(0, semantic.length() - Semantic.ANOMALY_SUFFIX.length());
            seriesName = mapping.getHeaderBySemantic(semantic);
        }

        if (Strings.isNullOrEmpty(seriesName) || Strings.isNullOrEmpty(semantic)) {
            String errorMessage = "Cannot apply running median filter to %s.".formatted(seriesName);
            MessageBoxHelper.showError(errorMessage, "");
            return;
        }

        String anomalySemantic = semantic + Semantic.ANOMALY_SUFFIX;
        SensorData anomalyColumn = mapping.getDataValueBySemantic(anomalySemantic);
        if (anomalyColumn == null) {
            String errorMessage = """
                    Cannot apply running median filter to "%s" \
                    because there is no "%s" field \
                    defined in import template.
                    """.formatted(seriesName, anomalySemantic);
            MessageBoxHelper.showError(errorMessage, "");
            return;
        }
        String filteredSeriesName = anomalyColumn.getHeader();

        LineChartWithMarkers chart = getDataChart(seriesName);
        if (chart == null) {
            return;
        }
        PlotData data = chart.filteredData != null && !chart.filteredData.data().isEmpty()
                ? chart.filteredData
                : chart.plotData;

        List<Number> values = data.data;
        List<Number> filtered = new ArrayList<>(values.size());
        for (Range range : file.getLineRanges().values()) {
            int from = range.getMin().intValue();
            int to = range.getMax().intValue();

            List<Number> rangeValues = values.subList(from, to + 1);
            MedianCorrectionFilter medianCorrection = new MedianCorrectionFilter(window);
            rangeValues = medianCorrection.apply(rangeValues);

            // put all back
            filtered.addAll(rangeValues);
        }

        for (int i = 0; i < file.getGeoData().size(); i++) {
            file.getGeoData().get(i).setSensorValue(filteredSeriesName, filtered.get(i));
        }
        file.setUnsaved(true);

        Platform.runLater(() -> {
            LineChartWithMarkers filteredChart = charts.get(filteredSeriesName);
            if (filteredChart == null) {
                PlotData filteredData = new PlotData(
                        filteredSeriesName,
                        chart.plotData.unit,
                        chart.plotData.color,
                        filtered
                );
                ValueAxis<Number> yAxis = createYAxis(filteredData);
                createLineChart(filteredData, xAxis, yAxis);
            } else {
                filteredChart.plotData = filteredChart.plotData.withData(filtered);
                filteredChart.filteredData = null;
                filteredChart.rescaleY();
                filteredChart.updateLineChartData();
            }
            updateChartName();
        });
    }

    private Map<String, LineChartWithMarkers> getCharts() {
        return charts;
    }

    private LineChartWithMarkers getDataChart(String seriesName) {
        // search chart by series name (excluding line markers)
        if (seriesName == null) {
            return null;
        }
        String lineHeader = GeoData.getHeader(Semantic.LINE, file.getTemplate());
        if (seriesName.equals(lineHeader)) {
            return null;
        }
        return charts.get(seriesName);
    }

    private static String emphasizeStyle(String baseStyle) {
        return baseStyle + "-fx-stroke-width: 2.0px;";
    }

    public boolean isSameTemplate(CsvFile selectedFile) {
        return file.isSameTemplate(selectedFile);
    }

    public String getSelectedSeriesName() {
        return selectedSeriesName;
    }

    public Set<String> getSeriesNames() {
        return Collections.unmodifiableSet(charts.keySet());
    }

    @Override
    public CsvFile getFile() {
        return file;
    }

    @Override
    public void selectTrace(TraceKey trace, boolean focus) {
        if (trace == null) {
            // clear selection
            clearSelectionMarker();
            return;
        }

        // TODO focus does not affect behavior

        int selectedX = trace.getIndex();
        ValueAxis<Number> xAxis = (ValueAxis<Number>)lastLineChart.getXAxis();
        var dataSize = lastLineChart.plotData.data().size();

        if (selectedX < 0 || selectedX > dataSize) {
            log.error("Selected trace index: {} is out of range: {}", selectedX, dataSize);
            return;
        }

        log.debug("Selected trace index: {}", selectedX);

        if (xAxis.getLowerBound() > selectedX || xAxis.getUpperBound() < selectedX) {

            int delta = (int)(xAxis.getUpperBound() - xAxis.getLowerBound());

            int lowerIndex = Math.clamp(selectedX - delta / 2, 0, dataSize - delta);
            int upperIndex = Math.clamp(selectedX + delta / 2, delta, dataSize);

            log.debug("Shifted charts, lowerIndex: {} upperIndex: {} size: {}", lowerIndex, upperIndex, dataSize);

            for (LineChartWithMarkers chart : charts.values()) {
                var yAxis = (ValueAxis<Number>) chart.getYAxis();

                ZoomRect zoomRect = new ZoomRect(lowerIndex, upperIndex, yAxis.getLowerBound(), yAxis.getUpperBound());
                chart.setZoomRect(zoomRect);
                chart.updateLineChartData();
            }

            model.publishEvent(new WhatChanged(this, WhatChanged.Change.csvDataZoom));
        }
        putSelectionMarker(selectedX);
        updateProfileScroll();
    }

    @Override
    public List<FoundPlace> getFlags() {
        return new ArrayList<>(foundPlaces.keySet());
    }

    @Override
    public void selectFlag(FoundPlace flag) {
        foundPlaces.keySet().forEach(f -> {
            var markerBox = (Pane) foundPlaces.get(f).getNode();
            Line l = (Line) markerBox.getChildren().get(1);
            l.setStrokeType(StrokeType.CENTERED);
            var fm = (Pane) ((Pane) markerBox.getChildren().get(0)).getChildren().get(0);
            fm.getChildren().stream().filter(ch -> ch instanceof Shape).forEach(
                    ch -> ((Shape) ch).setStrokeType(StrokeType.CENTERED)
            );
            f.setSelected(false);
        });

        // null -> clear selection
        if (flag != null) {
            var markerBox = (Pane) foundPlaces.get(flag).getNode();
            Line l = (Line) markerBox.getChildren().get(1);
            l.setStrokeType(StrokeType.OUTSIDE);
            var fm = (Pane) ((Pane) markerBox.getChildren().get(0)).getChildren().get(0);
            fm.getChildren().stream().filter(ch -> ch instanceof Shape).forEach(
                    ch -> ((Shape) ch).setStrokeType(StrokeType.OUTSIDE)
            );
            flag.setSelected(true);
        }
    }

    @Override
    public void addFlag(FoundPlace flag) {
        if (!foundPlaces.containsKey(flag)) {
            putFoundPlace(flag);
            clearSelectionMarker();
            if (flag.isSelected()) {
                selectFlag(flag);
            }
        }
    }

    @Override
    public void removeFlag(FoundPlace flag) {
        Data<Number, Number> marker = foundPlaces.remove(flag);
        if (marker != null && lastLineChart != null) {
            lastLineChart.removeVerticalValueMarker(marker);
        }
    }

    @Override
    public void clearFlags() {
        if (lastLineChart != null) {
            for (Data<Number, Number> marker : foundPlaces.values()) {
                lastLineChart.removeVerticalValueMarker(marker);
            }
        }
        foundPlaces.clear();
    }
}