package com.ugcs.geohammer.chart.csv;

import java.io.File;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.ugcs.geohammer.AppContext;
import com.ugcs.geohammer.chart.Chart;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.model.TraceKey;
import com.ugcs.geohammer.view.ResourceImageHolder;
import com.ugcs.geohammer.service.TraceTransform;
import com.ugcs.geohammer.view.MessageBoxHelper;
import com.ugcs.geohammer.model.TraceUnit;
import com.ugcs.geohammer.model.element.FoundPlace;
import com.ugcs.geohammer.chart.csv.axis.SensorLineChartXAxis;
import com.ugcs.geohammer.chart.csv.axis.SensorLineChartYAxis;
import com.ugcs.geohammer.model.event.FileClosedEvent;
import com.ugcs.geohammer.math.filter.LowPassFilter;
import com.ugcs.geohammer.math.filter.MedianCorrectionFilter;
import com.ugcs.geohammer.math.filter.SequenceFilter;
import com.ugcs.geohammer.math.filter.TimeLagFilter;
import com.ugcs.geohammer.model.Column;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.format.csv.parser.ColumnView;
import com.ugcs.geohammer.model.Semantic;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
import com.ugcs.geohammer.model.event.FileUpdatedEvent;
import com.ugcs.geohammer.model.event.SeriesUpdatedEvent;
import com.ugcs.geohammer.model.event.WhatChanged;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.ColorPalette;
import com.ugcs.geohammer.util.IndexRange;
import com.ugcs.geohammer.util.Nodes;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Range;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.util.Views;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Side;
import javafx.scene.Cursor;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.ValueAxis;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
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

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.model.Model;

import javafx.beans.InvalidationListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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

public class SensorLineChart extends Chart {

    private static final Logger log = LoggerFactory.getLogger(SensorLineChart.class);

    private static final double ZOOM_STEP = 1.38;

    private static final Color LINE_COLOR = Color.web("0xdf818eff");

    private final CsvFile file;

    private @Nullable final String lineSeriesName;

    // series name -> chart
    private final Map<String, LineChartWithMarkers> charts = new HashMap<>();

    private final LineChartWithMarkers interactiveChart;

    private @Nullable String selectedSeriesName;

    private final Map<FoundPlace, Data<Number, Number>> flagMarkers = new HashMap<>();

    private @Nullable Data<Number, Number> selectionMarker;

    // view

    private final VBox root;

    private final Label chartName;

    private final StackPane chartsContainer;

    private final Rectangle selectionRect = new Rectangle();

    private Viewport viewport = new Viewport(0, 1, 0, 1);

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private @Nullable ScheduledFuture<?> updateTask;

    static {
        Runtime.getRuntime().addShutdownHook(
                new Thread(scheduler::shutdownNow));
    }

    public SensorLineChart(Model model, CsvFile file) {
        super(model);

        this.file = Check.notNull(file);

        chartName = new Label();
        chartName.setFont(Font.font("Verdana", FontWeight.BOLD, 8));
        chartName.setTextFill(Color.rgb(60, 60, 60));
        updateChartName();

        // using StackPane to overlay charts
        chartsContainer = new StackPane();

        ImageView close = ResourceImageHolder.getImageView("close.png");
        close.setPickOnBounds(true);
        close.addEventHandler(MouseEvent.MOUSE_ENTERED, event -> {
            close.setCursor(Cursor.HAND);
        });
        close.setOnMouseClicked(event -> {
            event.consume();
            close();
        });

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
            model.selectChart(this);
        });

        // lookup line header
        ColumnSchema schema = GeoData.getSchema(file.getGeoData());
        lineSeriesName = schema != null
                ? schema.getHeaderBySemantic(Semantic.LINE.getName())
                : null;

        Set<String> displayHeaders = schema != null
                ? schema.getDisplayHeaders()
                : Set.of();
        for (String seriesName : displayHeaders) {
            Plot plot = createPlot(seriesName, file.getGeoData());
            createChart(plot);
        }

        // create interactive chart
        interactiveChart = createInteractiveChart();

        initRemoveLineMarkers();
        initFlagMarkers();

        getProfileScroll().setChangeListener((observable, oldVal, newVal) -> {
            zoomToProfileScroll();
        });

        applyViewport(false);
    }

    private Plot createPlot(String seriesName, List<GeoData> values) {
        Check.notEmpty(seriesName);
        ColumnSchema schema = GeoData.getSchema(values);
        if (schema == null) {
            return createEmptyPlot(seriesName, values.size());
        }
        Column column = schema.getColumn(seriesName);
        return new Plot(
                seriesName,
                column != null ? column.getUnit() : null,
                new ColumnView(values, seriesName));
    }

    private Plot createEmptyPlot(String seriesName, int numValues) {
        return new Plot(
                seriesName,
                Strings.empty(),
                Collections.nCopies(numValues, null));
    }

    private LineChartWithMarkers createInteractiveChart() {
        Plot plot = createEmptyPlot(Strings.empty(), numTraces());
        ValueAxis<Number> xAxis = createXAxisWithUnits();
        ValueAxis<Number> yAxis = createYAxis(plot);

        LineChartWithMarkers chart = new LineChartWithMarkers(xAxis, yAxis, plot);
        chart.setMouseTransparent(false);
        chart.zoomTo(viewport);

        setSelectionHandlers(chart);
        setScrollHandlers(chart);
        setClickHandlers(chart);

        chartsContainer.getChildren().add(chart);
        return chart;
    }

    private LineChartWithMarkers createChart(Plot plot) {
        ValueAxis<Number> xAxis = createXAxis();
        ValueAxis<Number> yAxis = createYAxis(plot);

        LineChartWithMarkers chart = new LineChartWithMarkers(xAxis, yAxis, plot);
        chart.setMouseTransparent(true);
        chart.zoomTo(viewport);

        chartsContainer.getChildren().add(chart);
        charts.put(plot.getSeriesName(), chart);
        return chart;
    }

    private void removeChart(String seriesName) {
        LineChartWithMarkers removed = charts.remove(seriesName);
        if (removed != null) {
            Platform.runLater(() -> {
                chartsContainer.getChildren().remove(removed);
            });
        }
    }

    private ValueAxis<Number> createXAxisWithUnits() {
        SensorLineChartXAxis xAxis = new SensorLineChartXAxis(model, file, 10);
        xAxis.setSide(Side.BOTTOM);
        xAxis.setPrefHeight(50);
        xAxis.setMinorTickVisible(false);

        xAxis.setAutoRanging(false);
        xAxis.setLowerBound(0);
        xAxis.setUpperBound(Math.max(0, numTraces() - 1));

        return xAxis;
    }

    private ValueAxis<Number> createXAxis() {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setSide(Side.BOTTOM);
        xAxis.setPrefHeight(50);
        xAxis.setMinorTickVisible(false);
        xAxis.setTickMarkVisible(false);
        xAxis.setTickLabelsVisible(false);

        int numTraces = numTraces();
        double tickUnit = numTraces > 0 ? numTraces / 10.0 : 1.0;
        xAxis.setTickUnit(tickUnit);

        xAxis.setAutoRanging(false);
        xAxis.setLowerBound(0);
        xAxis.setUpperBound(Math.max(0, numTraces - 1));

        return xAxis;
    }

    private ValueAxis<Number> createYAxis(Plot plot) {
        SensorLineChartYAxis yAxis = new SensorLineChartYAxis(10);
        yAxis.setLabel(Strings.nullToEmpty(plot.getUnit()));
        yAxis.setSide(Side.RIGHT); // Y-axis on the right
        yAxis.setPrefWidth(70);
        yAxis.setMinorTickVisible(false);

        Range yRange = plot.getDisplayRange();
        yAxis.setAutoRanging(false);
        yAxis.setLowerBound(yRange.getMin().doubleValue());
        yAxis.setUpperBound(yRange.getMax().doubleValue());

        return yAxis;
    }

    // chart event handlers

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

            // y-axis is reversed in a zoom space
            Point2D zoomStart = chart.normalizeToBounds(new Point2D(startX, endY));
            Point2D zoomEnd = chart.normalizeToBounds(new Point2D(endX, startY));
            Viewport selected = new Viewport(zoomStart.getX(), zoomEnd.getX(), zoomStart.getY(), zoomEnd.getY());
            setViewport(viewport.fit(selected), false);
        });
    }

    private void setScrollHandlers(LineChartWithMarkers chart) {
        chart.setOnScroll((ScrollEvent event) -> {
            boolean scaleY = !event.isControlDown();
            double scaleFactor = 1.1;
            if (event.getDeltaY() > 0) {
                scaleFactor = 1 / scaleFactor;
            }

            Point2D scaleCenter = chart.normalizeToBounds(
                    new Point2D(event.getX(), event.getY()));
            Viewport scaled = viewport.scale(
                    scaleFactor,
                    scaleY ? scaleFactor : 1,
                    scaleCenter.getX(),
                    scaleCenter.getY());
            setViewport(scaled, true);

            event.consume(); // don't scroll parent pane
        });
    }

    private void setClickHandlers(LineChartWithMarkers chart) {
        chart.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                Axis<Number> xAxis = chart.getXAxis();
                Point2D point = xAxis.screenToLocal(event.getScreenX(), event.getScreenY());
                Number xValue = xAxis.getValueForDisplay(point.getX());

                int traceIndex = xValue.intValue();
                if (traceIndex >= 0 && traceIndex < file.numTraces()) {
                    TraceKey trace = new TraceKey(file, traceIndex);
                    model.selectTrace(trace);
                    model.focusMapOnTrace(trace);
                }
                event.consume();
            }
        });
    }

    @Override
    public CsvFile getFile() {
        return file;
    }

    @Override
    public VBox getRootNode() {
        return root;
    }

    public Set<String> getSeriesNames() {
        return Collections.unmodifiableSet(charts.keySet());
    }

    public @Nullable String getSelectedSeriesName() {
        return selectedSeriesName;
    }

    private @Nullable LineChartWithMarkers getChart(@Nullable String seriesName) {
        // search chart by series name (excluding line markers)
        if (seriesName == null || seriesName.equals(lineSeriesName)) {
            return null;
        }
        return charts.get(seriesName);
    }

    private @Nullable LineChartWithMarkers getSelectedChart() {
        return selectedSeriesName != null ? charts.get(selectedSeriesName) : null;
    }

    public double getSeriesMinValue() {
        LineChartWithMarkers selectedChart = getSelectedChart();
        return selectedChart != null ? selectedChart.plot.getDataRange().getMin().doubleValue() : 0.0;
    }

    public double getSeriesMaxValue() {
        LineChartWithMarkers selectedChart = getSelectedChart();
        return selectedChart != null ? selectedChart.plot.getDataRange().getMax().doubleValue() : 0.0;
    }

    @Override
    public int numTraces() {
        return file.getGeoData().size();
    }

    @Override
    public int numVisibleTraces() {
        return getVisibleRange().size();
    }

    public IndexRange getVisibleRange() {
        int numTraces = numTraces();
        if (numTraces == 0) {
            return new IndexRange(0, 0);
        }

        int xWidth = numTraces - 1;
        int xMin = (int)Math.ceil(viewport.xMin() * xWidth);
        int xMax = (int)Math.floor(viewport.xMax() * xWidth);
        xMin = Math.clamp(xMin, 0, xWidth);
        xMax = Math.clamp(xMax, xMin, xWidth);

        return new IndexRange(xMin, xMax + 1);
    }

    private boolean isValueVisible(int index) {
        return getVisibleRange().contains(index);
    }

    public int getValueLineIndex(int index) {
        List<GeoData> values = file.getGeoData();
        if (values.isEmpty()) {
            return 0;
        }
        // correct out of range values to point to the first or last trace
        index = Math.max(0, Math.min(index, values.size() - 1));
        return values.get(index).getLineOrDefault(0);
    }

    public int getViewLineIndex() {
        LineChartWithMarkers selectedChart = getSelectedChart();
        if (selectedChart != null) {
            ValueAxis<Number> xAxis = (ValueAxis<Number>) selectedChart.getXAxis();
            int xCenter = (int) (0.5 * (xAxis.getLowerBound() + xAxis.getUpperBound()));
            return getValueLineIndex(xCenter);
        }
        // default: first range key or 0
        NavigableMap<Integer, IndexRange> lineRanges = file.getLineRanges();
        return !lineRanges.isEmpty() ? lineRanges.firstKey() : 0;
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

    // updates

    @Override
    public void selectFile() {
        model.publishEvent(new FileSelectedEvent(this, file));
    }

    public void selectChart(@Nullable String seriesName) {
        if (Objects.equals(seriesName, selectedSeriesName)) {
            return;
        }
        // clear current selection
        LineChartWithMarkers selectedChart = charts.get(selectedSeriesName);
        if (selectedChart != null) {
            selectedChart.setSelected(false);
        }
        selectedSeriesName = null;
        // select chart
        if (seriesName != null) {
            LineChartWithMarkers chart = charts.get(seriesName);
            if (chart != null) {
                chart.setSelected(true);
                selectedSeriesName = seriesName;
            }
        }
        if (model.isChartSelected(this)) {
            selectFile();
        }
    }

    public void showChart(String seriesName, boolean show) {
        LineChartWithMarkers chart = charts.get(seriesName);
        if (chart != null) {
            chart.setSeriesVisible(show);
        }
    }

    public void updateChartName() {
        File source = file.getFile();
        String fileName = (file.isUnsaved() ? "*" : "")
                + (source != null ? source.getName() : "Noname");
        chartName.setText(fileName);
    }

    private void updateCharts() {
        for (LineChartWithMarkers chart : charts.values()) {
            chart.updateData();
        }
    }

    public void updateXAxisUnits(TraceUnit traceUnit) {
        if (interactiveChart.getXAxis() instanceof SensorLineChartXAxis axisWithUnits) {
            Platform.runLater(() -> axisWithUnits.setUnit(traceUnit));
        }
    }

    @Override
    public void reload() {
        // clear selection
        clearSelectionMarker();
        // clear flags and markers
        clearFlags();
        interactiveChart.clearMarkers();

        ColumnSchema schema = GeoData.getSchema(file.getGeoData());
        Set<String> displayHeaders = schema != null
                ? schema.getDisplayHeaders()
                : Set.of();
        for (String seriesName : displayHeaders) {
            Plot plot = createPlot(seriesName, file.getGeoData());
            LineChartWithMarkers chart = charts.get(seriesName);
            if (chart == null) {
                chart = createChart(plot);
            } else {
                chart.setPlot(plot);
            }
        }

        Set<String> seriesToRemove = new HashSet<>(charts.keySet());
        seriesToRemove.removeAll(displayHeaders);
        seriesToRemove.forEach(this::removeFileColumn);

        // update interactive chart
        interactiveChart.setPlot(createEmptyPlot(Strings.empty(), numTraces()));

        // put new line markers and reposition flags
        initRemoveLineMarkers();
        initFlagMarkers();

        // restore selection
        TraceKey selectedTrace = model.getSelectedTrace(this);
        if (selectedTrace != null) {
            selectTrace(selectedTrace, false);
        }

        applyViewport(false);
    }

    private void close() {
        if (!confirmUnsavedChanges()) {
            return;
        }
        close(true);
    }

    public void close(boolean removeFromModel) {
        if (root.getParent() instanceof VBox parent) {
            // hide profile scroll
            getProfileScroll().setVisible(false);
            // remove charts
            parent.getChildren().remove(root);
            if (removeFromModel) {
                // remove files and traces from map
                model.publishEvent(new FileClosedEvent(this, file));
            }
        }
    }

    // zoom

    private void setViewport(Viewport viewport, boolean delayUpdate) {
        int lineIndexBefore = getSelectedLineIndex();

        this.viewport = viewport;
        applyViewport(delayUpdate);

        int lineIndexAfter = getSelectedLineIndex();
        if (lineIndexBefore != lineIndexAfter) {
            // request redraw on selected line index update
            model.publishEvent(new WhatChanged(SensorLineChart.this, WhatChanged.Change.justdraw));
        }
    }

    private void applyViewport(boolean delayUpdate) {
        // update scroll control
        updateProfileScroll();

        // zoom all charts to viewport
        interactiveChart.zoomTo(viewport);
        for (LineChartWithMarkers chart : charts.values()) {
            chart.zoomTo(viewport);
        }

        // update charts
        if (delayUpdate) {
            scheduleUpdate(() -> Platform.runLater(this::updateCharts), 300);
        } else {
            Platform.runLater(this::updateCharts);
        }

        // raise scroll event
        model.publishEvent(new WhatChanged(this, WhatChanged.Change.csvDataZoom));
    }

    private void scheduleUpdate(Runnable update, long delayMillis) {
        if (updateTask != null) {
            updateTask.cancel(false);
        }
        updateTask = scheduler.schedule(update, delayMillis, TimeUnit.MILLISECONDS);
    }

    private void updateProfileScroll() {
        IndexRange range = getVisibleRange();

        // num visible traces
        int numVisibleTraces = range.size();
        setMiddleTrace(range.from() + numVisibleTraces / 2);

        // hScale = scroll width / num visible traces
        // aspect ratio = hScale / vScale
        double hScale = getProfileScroll().getWidth()
                / (numVisibleTraces != 0 ? numVisibleTraces : 1);
        double aspectRatio = hScale / getVScale();
        setRealAspect(aspectRatio);

        // update scroll view
        getProfileScroll().recalc();
    }

    private void zoomToProfileScroll() {
        // num visible traces = scroll width / hscale
        int numVisibleTraces = (int)(getProfileScroll().getWidth() / getHScale());
        int numTraces = numTraces();

        int middle = getMiddleTrace();
        int w = numVisibleTraces / 2;

        // adjust visible range to the full range bounds
        middle = Math.max(middle, w);
        middle = Math.min(middle, numTraces - w - 1);
        // x-axis width for a full scale
        int xWidth = numTraces > 0 ? numTraces - 1 : 0;
        Viewport scrolled = new Viewport(
                xWidth > 0 ? (double)(middle - w) / xWidth : 0,
                xWidth > 0 ? (double)(middle + w) / xWidth : 0,
                viewport.yMin(),
                viewport.yMax()
        );
        setViewport(scrolled, true);
    }

    private void zoomToLine(int lineIndex) {
        IndexRange range = file.getLineRanges().get(lineIndex);
        int numTraces = numTraces();
        Viewport lineViewport = new Viewport(
                (double)range.from() / numTraces,
                (double)range.to() / numTraces,
                0,
                1);
        setViewport(lineViewport, false);
    }

    @Override
    public void zoomToCurrentLine() {
        int lineIndex = getSelectedLineIndex();
        zoomToLine(lineIndex);
    }

    @Override
    public void zoomToPreviousLine() {
        int lineIndex = getViewLineIndex();
        NavigableMap<Integer, IndexRange> lineRanges = file.getLineRanges();
        int firstLineIndex = !lineRanges.isEmpty() ? lineRanges.firstKey() : 0;
        zoomToLine(Math.max(lineIndex - 1, firstLineIndex));
    }

    @Override
    public void zoomToNextLine() {
        int lineIndex = getViewLineIndex();
        NavigableMap<Integer, IndexRange> lineRanges = file.getLineRanges();
        int lastLineIndex = !lineRanges.isEmpty() ? lineRanges.lastKey() : 0;
        zoomToLine(Math.min(lineIndex + 1, lastLineIndex));
    }

    @Override
    public void zoomToFit() {
        Viewport full = new Viewport(0, 1, 0, 1);
        setViewport(full, false);
    }

    @Override
    public void zoomIn() {
        Viewport scaled = viewport.scale(1.0 / ZOOM_STEP, 1.0);
        setViewport(scaled, false);
    }

    @Override
    public void zoomOut() {
        Viewport scaled = viewport.scale(ZOOM_STEP, 1.0);
        setViewport(scaled, false);
    }

    // markers

    @Override
    public void selectTrace(@Nullable TraceKey trace, boolean focus) {
        if (trace == null) {
            // clear selection
            clearSelectionMarker();
            return;
        }

        int traceIndex = trace.getIndex();
        int numTraces = numTraces();
        if (traceIndex < 0 || traceIndex >= numTraces) {
            log.error("Selected trace index {} is out of range: {}", traceIndex, numTraces);
            return;
        }

        double x = numTraces > 0 ? (double)traceIndex / numTraces : 0;
        if (x < viewport.xMin() || x > viewport.xMax()) {
            setViewport(viewport.moveToX(x), false);
        }
        createSelectionMarker(traceIndex);
    }

    public void createSelectionMarker(int traceIndex) {
        if (selectionMarker != null) {
            interactiveChart.removeMarker(selectionMarker);
        }
        selectionMarker = new Data<>(traceIndex, 0);
        interactiveChart.addMarker(selectionMarker);
    }

    public void clearSelectionMarker() {
        if (selectionMarker != null) {
            interactiveChart.removeMarker(selectionMarker);
            selectionMarker = null;
        }
    }

    private void initRemoveLineMarkers() {
        file.getLineRanges().forEach((lineIndex, lineRange) -> {
            createRemoveLineMarker(lineIndex, lineRange.from());
        });
    }

    private void createRemoveLineMarker(int lineIndex, int traceIndex) {
        Data<Number, Number> removeLineMarker = new Data<>(traceIndex, 0);

        Line line = new Line();
        line.setStroke(LINE_COLOR);
        line.setStrokeWidth(0.8);

        Pane icon = createRemoveLineIcon(lineIndex);

        interactiveChart.addMarker(
                removeLineMarker,
                line,
                null,
                icon,
                false);
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

    private void initFlagMarkers() {
        file.getAuxElements().forEach(it -> {
            if (it instanceof FoundPlace flag) {
                createFlagMarker(flag);
            }
        });
    }

    private void createFlagMarker(FoundPlace flag) {
        Data<Number, Number> flagMarker = new Data<>(flag.getTraceIndex(), 0);

        Line line = new Line();
        line.setStroke(Views.fxColor(flag.getFlagColor()));
        line.setStrokeWidth(0.8);
        line.setTranslateY(46);

        Pane flagIcon = createFlagIcon(flag);
        flagIcon.setTranslateY(28);

        interactiveChart.addMarker(
                flagMarker,
                line,
                null,
                flagIcon,
                false);

        flagMarkers.put(flag, flagMarker);
    }

    private Pane createFlagIcon(FoundPlace flag) {
        Line flagPole = new Line(0, 0, 0, 18);
        flagPole.setStroke(Color.BLACK);
        flagPole.setStrokeWidth(0.8);

        Polygon flagCloth = new Polygon();
        flagCloth.getPoints().addAll(0.0, 0.0,
                15.0, 0.0,
                10.0, 5.0,
                15.0, 10.0,
                0.0, 10.0);
        flagCloth.setFill(Views.fxColor(flag.getFlagColor()));
        flagCloth.setStroke(Color.BLACK);
        flagCloth.setStrokeWidth(0.8);

        Pane flagIcon = new Pane();
        flagIcon.getChildren().addAll(flagPole, flagCloth);

        flagIcon.addEventHandler(MouseEvent.MOUSE_ENTERED, event -> {
            flagIcon.setCursor(Cursor.HAND);
        });
        flagIcon.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            model.selectChart(this);
            selectFlag(flag);
            flag.mousePressHandle(new Point2D(event.getScreenX(), event.getScreenY()), this);
            event.consume();
        });
        flagIcon.addEventHandler(MouseEvent.MOUSE_RELEASED, event -> {
            event.consume();
        });

        return flagIcon;
    }

    @Override
    public List<FoundPlace> getFlags() {
        return new ArrayList<>(flagMarkers.keySet());
    }

    @Override
    public void selectFlag(@Nullable FoundPlace flag) {
        flagMarkers.keySet().forEach(f -> {
            var markerBox = (Pane) flagMarkers.get(f).getNode();
            Line l = (Line) markerBox.getChildren().get(1);
            l.setStrokeType(StrokeType.CENTERED);
            var fm = (Pane) ((Pane) markerBox.getChildren().getFirst()).getChildren().getFirst();
            fm.getChildren().stream().filter(ch -> ch instanceof Shape).forEach(
                    ch -> ((Shape) ch).setStrokeType(StrokeType.CENTERED)
            );
            f.setSelected(false);
        });

        // null -> clear selection
        if (flag != null) {
            var markerBox = (Pane) flagMarkers.get(flag).getNode();
            Line l = (Line) markerBox.getChildren().get(1);
            l.setStrokeType(StrokeType.OUTSIDE);
            var fm = (Pane) ((Pane) markerBox.getChildren().getFirst()).getChildren().getFirst();
            fm.getChildren().stream().filter(ch -> ch instanceof Shape).forEach(
                    ch -> ((Shape) ch).setStrokeType(StrokeType.OUTSIDE)
            );
            flag.setSelected(true);
        }
    }

    @Override
    public void addFlag(FoundPlace flag) {
        if (!flagMarkers.containsKey(flag)) {
            createFlagMarker(flag);
            clearSelectionMarker();
            if (flag.isSelected()) {
                selectFlag(flag);
            }
        }
    }

    @Override
    public void removeFlag(FoundPlace flag) {
        Data<Number, Number> flagMarker = flagMarkers.remove(flag);
        if (flagMarker != null) {
            interactiveChart.removeMarker(flagMarker);
        }
    }

    @Override
    public void clearFlags() {
        for (Data<Number, Number> flagMarker : flagMarkers.values()) {
            interactiveChart.removeMarker(flagMarker);
        }
        flagMarkers.clear();
    }

    // filters

    public void applyFilter(SequenceFilter filter, String seriesName, String filteredSeriesSuffix) {
        Check.notNull(filter);
        if (Strings.isNullOrEmpty(seriesName)) {
            return;
        }
        filteredSeriesSuffix = Strings.nullToEmpty(filteredSeriesSuffix);
        if (seriesName.endsWith(filteredSeriesSuffix)) {
            seriesName = seriesName.substring(0, seriesName.length() - filteredSeriesSuffix.length());
        }
        String filteredSeriesName = seriesName + filteredSeriesSuffix;

        LineChartWithMarkers chart = getChart(seriesName);
        if (chart == null) {
            String errorMessage = "Cannot apply filter to %s.".formatted(seriesName);
            MessageBoxHelper.showError(errorMessage, "");
            return;
        }

        List<@Nullable Number> values = chart.plot.data;
        List<@Nullable Number> filtered = new ArrayList<>(values.size());
        for (IndexRange range : file.getLineRanges().values()) {
            List<@Nullable Number> rangeValues = values.subList(range.from(), range.to());
            try {
                rangeValues = filter.apply(rangeValues);
            } catch (Exception e) {
                log.error("Filter error", e);
            }

            // put all back
            filtered.addAll(rangeValues);
        }

        createFileColumn(filteredSeriesName, seriesName);
        setFileColumnValues(filteredSeriesName, filtered);

        Platform.runLater(() -> {
            LineChartWithMarkers filteredChart = charts.get(filteredSeriesName);
            if (filteredChart == null) {
                Plot plot = createPlot(filteredSeriesName, file.getGeoData());
                filteredChart = createChart(plot);
            }
            model.publishEvent(new SeriesUpdatedEvent(this, file,
                    filteredSeriesName, true, true));
            filteredChart.updateData();
        });
    }

    private void createFileColumn(String header, @Nullable String protoHeader) {
        List<GeoData> values = file.getGeoData();
        Column column = GeoData.addColumn(values, header);
        if (column != null) {
            column.setDisplay(true);
            if (protoHeader != null) {
                Column proto = GeoData.getColumn(values, protoHeader);
                if (proto != null) {
                    column.setSemantic(proto.getSemantic());
                    column.setUnit(proto.getUnit());
                }
            }
            file.setUnsaved(true);
            Platform.runLater(this::updateChartName);
            model.publishEvent(new FileUpdatedEvent(this, file));
        }
    }

    public void removeFileColumn(String header) {
        List<GeoData> values = file.getGeoData();
        if (GeoData.removeColumn(values, header) != null) {
            file.setUnsaved(true);
            Platform.runLater(this::updateChartName);
            model.publishEvent(new FileUpdatedEvent(this, file));
        };
        removeChart(header);
    }

    private void setFileColumnValues(String header, List<@Nullable Number> data) {
        List<GeoData> values = file.getGeoData();
        for (int i = 0; i < data.size(); i++) {
            values.get(i).setValue(header, data.get(i));
        }
        file.setUnsaved(true);
        Platform.runLater(this::updateChartName);
    }

    public void applyTimeLag(String seriesName, int shift) {
        TimeLagFilter filter = new TimeLagFilter(shift);
        applyFilter(filter, seriesName, "_LAG");
    }

    public void applyLowPass(String seriesName, int order) {
        LowPassFilter filter = new LowPassFilter(order);
        applyFilter(filter, seriesName, "_LPF");
    }

    public void applyRunningMedian(String seriesName, int window) {
        MedianCorrectionFilter filter = new MedianCorrectionFilter(window);
        applyFilter(filter, seriesName, "_RM");
    }

    // chart

    class LineChartWithMarkers extends LineChart<Number, Number> {

        private static final int DEFAULT_VIEW_SAMPLES = 500;

        private static final int MIN_VIEW_SAMPLES = 200;

        private static final int MAX_VIEW_SAMPLES = 1000;

        private Plot plot;

        private final Series<Number, Number> series;

        private final ObservableList<Data<Number, Number>> markers;

        public LineChartWithMarkers(Axis<Number> xAxis, Axis<Number> yAxis, Plot plot) {
            super(xAxis, yAxis);

            this.plot = plot;

            series = new Series<>();
            getData().add(series);

            markers = FXCollections.observableArrayList(data -> new Observable[] {data.XValueProperty()});
            markers.addListener((InvalidationListener) observable -> layoutPlotChildren());

            initView();
        }

        private void initView() {
            setLegendVisible(false); // Hide legend
            setCreateSymbols(false); // Disable symbols
            lookup(".chart-plot-background").setStyle("-fx-background-color: transparent;");

            setSeriesVisible(false);
            setSelected(false);
        }

        public void setSelected(boolean selected) {
            setSeriesStyle(selected ? plot.getSelectedStyle() : plot.getStyle());
            setAxesVisible(selected);
        }

        public boolean isSeriesVisible() {
            return series.getNode().isVisible();
        }

        public void setSeriesVisible(boolean visible) {
            series.getNode().setVisible(visible);

            if (visible) {
                updateData();
            }
        }

        private void setAxesVisible(boolean visible) {
            setVerticalGridLinesVisible(visible);
            setHorizontalGridLinesVisible(visible);
            setHorizontalZeroLineVisible(visible);
            setVerticalZeroLineVisible(visible);
            getYAxis().setOpacity(visible ? 1 : 0);
        }

        public void setPlot(Plot plot) {
            this.plot = Check.notNull(plot);
        }

        public Point2D normalizeToBounds(Point2D chartPoint) {
            // point is in chart coordinates
            // translate chart coordinates to plot coordinates
            Node plot = lookup(".chart-plot-background");
            Bounds plotBounds = Nodes.getBoundsInParent(plot, this);
            Point2D plotPoint = new Point2D(
                    chartPoint.getX() - plotBounds.getMinX(),
                    chartPoint.getY() - plotBounds.getMinY());

            // convert point coordinates to x and y ratios [0, 1]
            double x = plotBounds.getWidth() > 1e-9
                    ? Math.clamp(plotPoint.getX() / plotBounds.getWidth(), 0, 1)
                    : 0;
            double y = plotBounds.getHeight() > 1e-9
                    ? Math.clamp(1 - plotPoint.getY() / plotBounds.getHeight(), 0, 1)
                    : 0;
            return new Point2D(x, y);
        }

        private void zoomTo(Viewport viewport) {
            int xWidth = !plot.getData().isEmpty() ? plot.getData().size() - 1 : 0;
            ValueAxis<Number> xAxis = (ValueAxis<Number>)getXAxis();
            xAxis.setLowerBound(viewport.xMin() * xWidth);
            xAxis.setUpperBound(viewport.xMax() * xWidth);

            Range yRange = plot.getDisplayRange();
            ValueAxis<Number> yAxis = (ValueAxis<Number>) getYAxis();
            yAxis.setLowerBound(yRange.getMin().doubleValue() + viewport.yMin() * yRange.getWidth());
            yAxis.setUpperBound(yRange.getMin().doubleValue() + viewport.yMax() * yRange.getWidth());
        }

        private void setSeriesStyle(String style) {
            Check.notNull(series);

            Node seriesNode = series.getNode();
            Node lineNode = seriesNode != null
                    ? seriesNode.lookup(".chart-series-line")
                    : null;
            if (lineNode != null) {
                lineNode.setStyle(style);
            }
        }

        private void updateData() {
            if (!isSeriesVisible()) {
                return; // don't update hidden charts
            }

            // x range
            ValueAxis<Number> xAxis = (ValueAxis<Number>)getXAxis();
            int xMin = (int)Math.ceil(xAxis.getLowerBound());
            int xMax = (int)Math.floor(xAxis.getUpperBound());
            xMin = Math.clamp(xMin, 0, plot.getData().size() - 1);
            xMax = Math.clamp(xMax, xMin, plot.getData().size() - 1);
            IndexRange range = new IndexRange(xMin, xMax + 1);

            int numSamples = Math.clamp((int)xAxis.getLayoutBounds().getWidth(), MIN_VIEW_SAMPLES, MAX_VIEW_SAMPLES);
            List<Data<Number, Number>> data = sampleSeriesData(range, numSamples);

            setSeriesData(data);
        }

        private void setSeriesData(List<Data<Number, Number>> data) {
            setAnimated(false);

            // clear current data
            series.getData().clear();
            if (data.isEmpty()) {
                series.getData().add(new Data<>(0, 0));
            } else {
                series.getData().addAll(data);
            }

            setAnimated(true);
        }

        private List<Data<Number, Number>> sampleSeriesData(IndexRange range, int numSamples) {
            int limit = Math.min(range.size(), numSamples);
            double step = Math.max(1, limit > 1 ? (double)(range.size() - 1) / (limit - 1) : 1);
            int hw = (int)(step / 2);

            List<Data<Number, Number>> samples = new ArrayList<>(limit);
            for (int k = 0; k < limit; k++) {
                int i = range.from() + (int)Math.round(k * step);
                if (plot.data.get(i) == null) {
                    continue;
                }
                int line = getValueLineIndex(i);
                double sum = 0.0;
                int count = 0;
                for (int j = i - hw; j <= i + hw; j++) {
                    if (j < 0 || j >= plot.data.size()) {
                        continue;
                    }
                    Number v = plot.data.get(j);
                    if (v != null && getValueLineIndex(j) == line) {
                        sum += v.doubleValue();
                        count++;
                    }
                }
                if (count > 0) {
                    samples.add(new Data<>(i, sum / count));
                }
            }
            return samples;
        }

        public void addMarker(Data<Number, Number> marker) {
            Line line = new Line();
            line.setStroke(Color.RED); // Bright color for white background
            line.setStrokeWidth(1);
            line.setTranslateY(15);

            ImageView imageView = ResourceImageHolder.getImageView("gps32.png");
            imageView.setTranslateY(17);

            addMarker(marker, line, imageView, null, true);
        }

        /**
         * Add a vertical value marker to the chart.
         *
         * @param marker    the data point to be marked
         * @param line      the line to be used for the marker
         * @param imageView the image to be displayed at the marker
         */
        public void addMarker(Data<Number, Number> marker, Line line,
                @Nullable ImageView imageView, @Nullable Pane imagePane,
                boolean mouseTransparent) {
            if (markers.contains(marker)) {
                return;
            }

            VBox markerBox = new VBox();
            markerBox.setAlignment(Pos.TOP_CENTER);
            markerBox.setMouseTransparent(mouseTransparent);

            VBox imageContainer = new VBox();
            imageContainer.setAlignment(Pos.TOP_CENTER);
            if (imageView != null) {
                imageContainer.getChildren().add(imageView);
            }
            if (imagePane != null) {
                imageContainer.getChildren().add(imagePane);
            }
            markerBox.getChildren().add(imageContainer);
            markerBox.getChildren().add(line);

            marker.setNode(markerBox);
            getPlotChildren().add(markerBox);

            markers.add(marker);
        }

        public void removeMarker(Data<Number, Number> marker) {
            if (marker.getNode() != null) {
                getPlotChildren().remove(marker.getNode());
                marker.setNode(null);
            }
            markers.remove(marker);
        }

        private void clearMarkers() {
            for (Data<Number, Number> marker : new ArrayList<>(markers)) {
                removeMarker(marker);
            }
        }

        @Override
        protected void layoutPlotChildren() {
            super.layoutPlotChildren();

            for (Data<Number, Number> verticalMarker : markers) {
                VBox markerBox = (VBox) verticalMarker.getNode();
                markerBox.setLayoutX(getXAxis().getDisplayPosition(verticalMarker.getXValue()));

                VBox imageContainer = (VBox) markerBox.getChildren().getFirst();
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
    }

    // data classes

    // chart viewport relative to [0, 1] full-size view
    record Viewport(double xMin, double xMax, double yMin, double yMax) {

        Viewport(double xMin, double xMax, double yMin, double yMax) {
            this.xMin = Math.clamp(xMin, 0, 1);
            this.xMax = Math.clamp(xMax, 0, 1);
            this.yMin = Math.clamp(yMin, 0, 1);
            this.yMax = Math.clamp(yMax, 0, 1);
        }

        public double width() {
            return xMax - xMin;
        }

        public double height() {
            return yMax - yMin;
        }

        public Viewport scale(double xScale, double yScale) {
            // scale center is in the middle
            return scale(xScale, yScale, 0.5, 0.5);
        }

        public Viewport scale(double xScale, double yScale, double xCenter, double yCenter) {
            Range xRange = new Range(xMin, xMax).scale(xScale, xCenter);
            Range yRange = new Range(yMin, yMax).scale(yScale, yCenter);
            return new Viewport(
                    xRange.getMin().doubleValue(), xRange.getMax().doubleValue(),
                    yRange.getMin().doubleValue(), yRange.getMax().doubleValue());
        }

        public Viewport fit(Viewport other) {
            // other viewport in a space of the current viewport
            // translate this viewport to match other but in its own
            // coordinate space
            return new Viewport(
                    xMin + other.xMin * width(),
                    xMin + other.xMax * width(),
                    yMin + other.yMin * height(),
                    yMin + other.yMax * height());
        }

        public Viewport moveToX(double x) {
            if (x >= xMin && x <= xMax) {
                return this;
            }
            double w = 0.5 * (xMax - xMin);
            x = Math.clamp(x, w, 1.0 - w);
            return new Viewport(x - w, x + w, yMin, yMax);
        }
    }

    public static class Plot {

        private final String seriesName;

        private final @Nullable String unit;

        private final Color color;

        private final List<@Nullable Number> data;

        private final Range dataRange;

        private final Range displayRange;

        public Plot(String seriesName, @Nullable String unit, List<@Nullable Number> data) {
            this.seriesName = seriesName;
            this.unit = unit;
            this.color = ColorPalette.highContrast().getColor(this.seriesName);
            this.data = data;
            this.dataRange = buildDataRange(data);
            this.displayRange = buildDisplayRange(this.dataRange);
        }

        public String getSeriesName() {
            return seriesName;
        }

        public @Nullable String getUnit() {
            return unit;
        }

        public String getStyle() {
            return "-fx-stroke: " + Views.toColorString(color) + ";" + "-fx-stroke-width: 0.6px;";
        }

        public String getSelectedStyle() {
            return "-fx-stroke: " + Views.toColorString(color) + ";" + "-fx-stroke-width: 1.4px;";
        }

        public List<@Nullable Number> getData() {
            return data;
        }

        public static Range buildDataRange(List<@Nullable Number> data) {
            Double min = null;
            Double max = null;
            for (Number value : Nulls.toEmpty(data)) {
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
            return new Range(min, max);
        }

        public Range getDataRange() {
            return dataRange;
        }

        public static double getScaleFactor(double value) {
            int base = (int)Math.clamp(Math.floor(Math.log10(Math.abs(value))), 0, 3);
            return Math.pow(10, base);
        }

        public static Range buildDisplayRange(Range dataRange) {
            double min = dataRange.getMin().doubleValue();
            double max = dataRange.getMax().doubleValue();
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

        public Range getDisplayRange() {
            return displayRange;
        }
    }
}