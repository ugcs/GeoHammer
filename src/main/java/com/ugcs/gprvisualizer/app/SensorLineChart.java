package com.ugcs.gprvisualizer.app;

import java.io.File;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.github.thecoldwine.sigrun.common.ext.*;
import com.ugcs.gprvisualizer.app.auxcontrol.FoundPlace;
import com.ugcs.gprvisualizer.app.axis.SensorLineChartXAxis;
import com.ugcs.gprvisualizer.app.axis.SensorLineChartYAxis;
import com.ugcs.gprvisualizer.app.events.FileClosedEvent;
import com.ugcs.gprvisualizer.app.filter.LowPassFilter;
import com.ugcs.gprvisualizer.app.filter.MedianCorrectionFilter;
import com.ugcs.gprvisualizer.app.filter.SequenceFilter;
import com.ugcs.gprvisualizer.app.filter.TimeLagFilter;
import com.ugcs.gprvisualizer.app.parsers.Column;
import com.ugcs.gprvisualizer.app.parsers.ColumnSchema;
import com.ugcs.gprvisualizer.app.parsers.ColumnView;
import com.ugcs.gprvisualizer.app.parsers.Semantic;
import com.ugcs.gprvisualizer.event.FileSelectedEvent;
import com.ugcs.gprvisualizer.event.SeriesAddedEvent;
import com.ugcs.gprvisualizer.event.SeriesRemovedEvent;
import com.ugcs.gprvisualizer.event.WhatChanged;
import com.ugcs.gprvisualizer.utils.Check;
import com.ugcs.gprvisualizer.utils.IndexRange;
import com.ugcs.gprvisualizer.utils.Nodes;
import com.ugcs.gprvisualizer.utils.Nulls;
import com.ugcs.gprvisualizer.utils.Range;
import com.ugcs.gprvisualizer.utils.Strings;
import com.ugcs.gprvisualizer.utils.Views;
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

import com.ugcs.gprvisualizer.app.parsers.GeoData;
import com.ugcs.gprvisualizer.gpr.Model;

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

    private @Nullable LineChartWithMarkers interactiveChart;

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

        if (interactiveChart != null) {
            setSelectionHandlers(interactiveChart);
            setScrollHandlers(interactiveChart);
            setClickHandlers(interactiveChart);
            initRemoveLineMarkers();
        }

        initFlagMarkers();

        getProfileScroll().setChangeListener((observable, oldVal, newVal) -> {
            zoomToProfileScroll();
            updateOnZoom(true);
        });

        updateProfileScroll();
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
                model.getColor(seriesName),
                new ColumnView(values, seriesName));
    }

    private Plot createEmptyPlot(String seriesName, int numValues) {
        return new Plot(
                seriesName,
                Strings.empty(),
                model.getColor(seriesName),
                Collections.nCopies(numValues, null));
    }

    private LineChartWithMarkers createChart(Plot plot) {
        // Creating chart
        boolean isInteractive = charts.isEmpty(); // first chart will be an interactive chart

        // Create axes
        ValueAxis<Number> xAxis = isInteractive ? createXAxisWithUnits() : createXAxis();
        ValueAxis<Number> yAxis = createYAxis(plot);

        LineChartWithMarkers chart = new LineChartWithMarkers(xAxis, yAxis, plot);
        chart.setMouseTransparent(!isInteractive);

        // Add chart to container
        chartsContainer.getChildren().add(chart);
        charts.put(plot.seriesName(), chart);
        if (isInteractive) {
            interactiveChart = chart;
        }
        return chart;
    }

    private void removeChart(String seriesName) {
        LineChartWithMarkers removed = charts.remove(seriesName);
        if (removed != null) {
            Platform.runLater(() -> {
                chartsContainer.getChildren().remove(removed);
            });
            model.publishEvent(new SeriesRemovedEvent(this, file, seriesName));
        }
    }

    private ValueAxis<Number> createXAxisWithUnits() {
        SensorLineChartXAxis xAxis = new SensorLineChartXAxis(model, file, 10);
        xAxis.setSide(Side.BOTTOM);
        xAxis.setPrefHeight(50);

        xAxis.setMinorTickVisible(false);

        xAxis.setLowerBound(0);
        xAxis.setUpperBound(file.getGeoData().size() - 1);

        return xAxis;
    }

    private ValueAxis<Number> createXAxis() {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setSide(Side.BOTTOM);
        xAxis.setPrefHeight(50);

        xAxis.setMinorTickVisible(false);
        xAxis.setTickMarkVisible(false);
        xAxis.setTickLabelsVisible(false);

        int numValues = file.getGeoData().size();
        double tickUnit = numValues > 0 ? numValues / 10.0 : 1.0;
        xAxis.setTickUnit(tickUnit);

        return xAxis;
    }

    private ValueAxis<Number> createYAxis(Plot plot) {
        SensorLineChartYAxis yAxis = new SensorLineChartYAxis(10);
        yAxis.setLabel(Strings.nullToEmpty(plot.unit()));
        yAxis.setSide(Side.RIGHT); // Y-axis on the right
        yAxis.setPrefWidth(70);
        yAxis.setMinorTickVisible(false);

        Range valueRange = plot.valueRange();
        yAxis.setLowerBound(valueRange.getMin().doubleValue());
        yAxis.setUpperBound(valueRange.getMax().doubleValue());

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

            // y axis is reversed in a zoom space
            Point2D zoomStart = chart.normalizeToBounds(new Point2D(startX, endY));
            Point2D zoomEnd = chart.normalizeToBounds(new Point2D(endX, startY));
            Viewport selectionViewport = new Viewport(zoomStart.getX(), zoomEnd.getX(), zoomStart.getY(), zoomEnd.getY());
            setZoom(viewport.fit(selectionViewport));
            updateOnZoom(false);
        });
    }

    private void setScrollHandlers(LineChartWithMarkers chart) {
        chart.setOnScroll((ScrollEvent event) -> {
            boolean scaleY = !event.isControlDown();
            double scaleFactor = 1.1;
            if (event.getDeltaY() > 0) {
                scaleFactor = 1 / scaleFactor;
            }

            Point2D scaleCenter = chart.normalizeToBounds(new Point2D(event.getX(), event.getY()));
            setZoom(viewport.scale(scaleFactor, scaleY ? scaleFactor : 1, scaleCenter));
            updateOnZoom(true);

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

    public boolean isSameTemplate(CsvFile other) {
        return file.isSameTemplate(other);
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
        return selectedChart != null ? selectedChart.plot.min() : 0.0;
    }

    public double getSeriesMaxValue() {
        LineChartWithMarkers selectedChart = getSelectedChart();
        return selectedChart != null ? selectedChart.plot.max() : 0.0;
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
        LineChartWithMarkers selectedChart = getSelectedChart();
        if (selectedChart == null) {
            return new IndexRange(0, 0);
        }
        // TODO obtain from viewport
        ValueAxis<Number> xAxis = (ValueAxis<Number>) selectedChart.getXAxis();
        return new IndexRange((int)xAxis.getLowerBound(), (int)xAxis.getUpperBound() + 1);
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
            selectedChart.showAxes(false);
        }
        selectedSeriesName = null;
        // select chart
        if (seriesName != null) {
            LineChartWithMarkers chart = charts.get(seriesName);
            if (chart != null) {
                chart.showAxes(true);
                selectedSeriesName = seriesName;
            }
        }
        if (model.isChartSelected(this)) {
            selectFile();
        }
        Platform.runLater(this::updateChartData);
    }

    public void showChart(String seriesName, boolean show) {
        LineChartWithMarkers chart = charts.get(seriesName);
        if (chart != null) {
            chart.showData(show);
        }
    }

    public void updateChartName() {
        File source = file.getFile();
        String fileName = (file.isUnsaved() ? "*" : "")
                + (source != null ? source.getName() : "Noname");
        chartName.setText(fileName);
    }

    private void updateChartData() {
        for (LineChartWithMarkers chart : charts.values()) {
            chart.updateChart();
        }
    }

    public void updateXAxisUnits(TraceUnit traceUnit) {
        for (LineChartWithMarkers chart : charts.values()) {
            if (chart.getXAxis() instanceof SensorLineChartXAxis axisWithUnits) {
                Platform.runLater(() -> axisWithUnits.setUnit(traceUnit));
            }
        }
    }

    @Override
    public void reload() {
        // clear selection
        clearSelectionMarker();
        // clear flags and markers
        clearFlags();
        if (interactiveChart != null) {
            interactiveChart.clearMarkers();
        }

        ColumnSchema schema = GeoData.getSchema(file.getGeoData());
        Set<String> displayHeaders = schema != null
                ? schema.getDisplayHeaders()
                : Set.of();
        for (String seriesName : displayHeaders) {
            Plot plot = createPlot(seriesName, file.getGeoData());
            LineChartWithMarkers chart = charts.get(seriesName);
            if (chart == null) {
                createChart(plot);
            } else {
                chart.setPlot(plot);
            }
        }

        Set<String> seriesToRemove = new HashSet<>(charts.keySet());
        seriesToRemove.removeAll(displayHeaders);
        seriesToRemove.forEach(this::removeFileColumn);

        // put new line markers and reposition flags
        initRemoveLineMarkers();
        initFlagMarkers();

        // restore selection
        TraceKey selectedTrace = model.getSelectedTrace(this);
        if (selectedTrace != null) {
            selectTrace(selectedTrace, false);
        }

        Platform.runLater(this::updateChartData);
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

    private void updateProfileScroll() {
        int numTraces = numTraces();
        int xMin = (int)(viewport.xMin * numTraces);
        int xMax = (int)(viewport.xMax * numTraces);

        // num visible traces
        int numVisibleTraces = xMax - xMin + 1;
        setMiddleTrace(xMin + numVisibleTraces / 2);

        // hScale = scroll width / num visible traces
        // aspect ratio = hScale / vScale
        double hScale = getProfileScroll().getWidth() / numVisibleTraces;
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

        // adjust visible range to the
        // full range bounds
        if (middle - w < 0) {
            middle = w;
        }
        if (middle + w >= numTraces) {
            middle = numTraces - w - 1;
        }

        setZoom(new Viewport(
                (double)(middle - w) / numTraces,
                (double)(middle + w) / numTraces,
                viewport.yMin(),
                viewport.yMax()));
    }

    private void zoomToLine(int lineIndex) {
        IndexRange range = file.getLineRanges().get(lineIndex);
        int numTraces = numTraces();
        setZoom(new Viewport(
                (double)range.from() / numTraces,
                (double)range.to() / numTraces,
                0,
                1
        ));
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
        NavigableMap<Integer, IndexRange> lineRanges = file.getLineRanges();
        int firstLineIndex = !lineRanges.isEmpty() ? lineRanges.firstKey() : 0;
        zoomToLine(Math.max(lineIndex - 1, firstLineIndex));
        updateOnZoom(false);
    }

    @Override
    public void zoomToNextLine() {
        int lineIndex = getViewLineIndex();
        NavigableMap<Integer, IndexRange> lineRanges = file.getLineRanges();
        int lastLineIndex = !lineRanges.isEmpty() ? lineRanges.lastKey() : 0;
        zoomToLine(Math.min(lineIndex + 1, lastLineIndex));
        updateOnZoom(false);
    }

    @Override
    public void zoomToFit() {
        setZoom(new Viewport(0, 1, 0, 1));
        updateOnZoom(false);
    }

    @Override
    public void zoomIn() {
        setZoom(viewport.scale(1.0 / ZOOM_STEP, 1.0, null));
        updateOnZoom(false);
    }

    @Override
    public void zoomOut() {
        setZoom(viewport.scale(ZOOM_STEP, 1.0, null));
        updateOnZoom(false);
    }

    private void setZoom(Viewport viewport) {
        this.viewport = viewport;
        applyZoomToCharts();
    }

    private void applyZoomToCharts() {
        for (LineChartWithMarkers chart : charts.values()) {
            chart.zoomTo(viewport);
        }
    }

    private void updateOnZoom(boolean delayed) {
        updateProfileScroll();
        model.publishEvent(new WhatChanged(this, WhatChanged.Change.csvDataZoom));

        if (delayed) {
            scheduleUpdate(() -> Platform.runLater(this::updateChartData), 300);
        } else {
            Platform.runLater(this::updateChartData);
        }
    }

    private void scheduleUpdate(Runnable update, long delayMillis) {
        if (updateTask != null) {
            updateTask.cancel(false);
        }
        updateTask = scheduler.schedule(update, delayMillis, TimeUnit.MILLISECONDS);
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
            setZoom(viewport.moveToX(x));
            updateOnZoom(false);
        }
        createSelectionMarker(traceIndex);
    }

    public void createSelectionMarker(int traceIndex) {
        if (interactiveChart != null) {
            if (selectionMarker != null) {
                interactiveChart.removeMarker(selectionMarker);
            }
            selectionMarker = new Data<>(traceIndex, 0);
            interactiveChart.addMarker(selectionMarker);
        }
    }

    public void clearSelectionMarker() {
        if (interactiveChart != null && selectionMarker != null) {
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

        if (interactiveChart != null) {
            interactiveChart.addMarker(
                    removeLineMarker,
                    line,
                    null,
                    icon,
                    false);
        }
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

        if (interactiveChart != null) {
            interactiveChart.addMarker(
                    flagMarker,
                    line,
                    null,
                    flagIcon,
                    false);
        }

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
        if (flagMarker != null && interactiveChart != null) {
            interactiveChart.removeMarker(flagMarker);
        }
    }

    @Override
    public void clearFlags() {
        if (interactiveChart != null) {
            for (Data<Number, Number> flagMarker : flagMarkers.values()) {
                interactiveChart.removeMarker(flagMarker);
            }
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
            updateChartName();

            LineChartWithMarkers filteredChart = charts.get(filteredSeriesName);
            if (filteredChart == null) {
                Plot plot = createPlot(filteredSeriesName, file.getGeoData());
                filteredChart = createChart(plot);
                model.publishEvent(new SeriesAddedEvent(this, file, filteredSeriesName));
            }
            filteredChart.updateChart();
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
        }
    }

    public void removeFileColumn(String header) {
        List<GeoData> values = file.getGeoData();
        GeoData.removeColumn(values, header);
        removeChart(header);
    }

    private void setFileColumnValues(String header, List<@Nullable Number> data) {
        List<GeoData> values = file.getGeoData();
        for (int i = 0; i < data.size(); i++) {
            values.get(i).setValue(header, data.get(i));
        }
        file.setUnsaved(true);
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

        private final Series<Number, Number> series;

        private final ObservableList<Data<Number, Number>> markers;

        private Plot plot;

        // loaded x range
        private Range sampleRange;

        public LineChartWithMarkers(Axis<Number> xAxis, Axis<Number> yAxis, Plot plot) {
            super(xAxis, yAxis);

            markers = FXCollections.observableArrayList(data -> new Observable[] {data.XValueProperty()});
            markers.addListener((InvalidationListener) observable -> layoutPlotChildren());

            Check.notNull(plot);
            this.plot = plot;

            series = new Series<>();
            getData().add(series);

            initView();

            zoomTo(viewport);
            updateChart();
        }

        private void initView() {
            setLegendVisible(false); // Hide legend
            setCreateSymbols(false); // Disable symbols
            lookup(".chart-plot-background").setStyle("-fx-background-color: transparent;");

            showAxes(false);
            showData(false);
        }

        public void showAxes(boolean show) {
            setVerticalGridLinesVisible(show);
            setHorizontalGridLinesVisible(show);
            setHorizontalZeroLineVisible(show);
            setVerticalZeroLineVisible(show);
            getYAxis().setOpacity(show ? 1 : 0);
        }

        public boolean isDataVisible() {
            return series.getNode().isVisible();
        }

        public void showData(boolean show) {
            series.getNode().setVisible(show);
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
            int lineIndexBefore = getSelectedLineIndex();

            int xWidth = plot.data.size();
            ValueAxis<Number> xAxis = (ValueAxis<Number>)getXAxis();
            xAxis.setAutoRanging(false);
            xAxis.setLowerBound(viewport.xMin * xWidth);
            xAxis.setUpperBound(viewport.xMax * xWidth);

            Range yRange = plot.dataRange;
            ValueAxis<Number> yAxis = (ValueAxis<Number>) getYAxis();
            yAxis.setAutoRanging(false);
            yAxis.setLowerBound(yRange.getMin().doubleValue() + viewport.yMin * yRange.getWidth());
            yAxis.setUpperBound(yRange.getMin().doubleValue() + viewport.yMax * yRange.getWidth());

            int lineIndexAfter = getSelectedLineIndex();
            if (lineIndexBefore != lineIndexAfter) {
                // request redraw on selected line index update
                model.publishEvent(new WhatChanged(SensorLineChart.this, WhatChanged.Change.justdraw));
            }
        }

        private static String emphasizeStyle(String style) {
            return style + "-fx-stroke-width: 1.25px;";
        }

        private static String dashStyle(String style) {
            return style + "-fx-stroke-dash-array: 1 5 1 5;";
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

        private void setSeriesData(int lowerIndex, int upperIndex) {
            Axis<Number> xAxis = getXAxis() ;
            int numSamples = Math.clamp((int) xAxis.getLayoutBounds().getWidth(), MIN_VIEW_SAMPLES, MAX_VIEW_SAMPLES);
            List<Data<Number, Number>> samples = getSamplesInRange(lowerIndex, upperIndex, numSamples);

            // clear current points
            setAnimated(false);
            series.getData().clear();

            if (samples.isEmpty()) {
                series.getData().add(new Data<>(0, 0));
            } else {
                series.getData().addAll(samples);
            }

            setAnimated(true);
        }

        private List<Data<Number, Number>> getSamplesInRange(int lowerIndex, int upperIndex, int numSamples) {
            int numValues = upperIndex - lowerIndex + 1;
            int limit = Math.min(numValues, numSamples);

            double step = Math.max(1, limit > 1 ? (double)(numValues - 1) / (limit - 1) : 1);
            int hw = (int)(step / 2);

            List<Data<Number, Number>> samples = new ArrayList<>(limit);
            for (int k = 0; k < limit; k++) {
                int i = lowerIndex + (int)Math.round(k * step);
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

        private void updateChart() {
            ValueAxis<Number> xAxis = (ValueAxis<Number>)getXAxis();
            int lowerIndex = Math.clamp((int)xAxis.getLowerBound(), 0, plot.data().size() - 1);
            int upperIndex = Math.clamp((int)xAxis.getUpperBound(), lowerIndex, plot.data().size() - 1);

            if (isDataVisible()) {
                String style = plot.style();
                if (Objects.equals(plot.seriesName(), selectedSeriesName)) {
                    style = emphasizeStyle(style);
                }
                setSeriesData(lowerIndex, upperIndex);
                setSeriesStyle(style);
            }
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

        public Viewport scale(double xScale, double yScale, @Nullable Point2D scaleCenter) {
            // when scale center is null zoom at center
            double xCenterRatio = scaleCenter != null ? scaleCenter.getX() : 0.5;
            double yCenterRatio = scaleCenter != null ? scaleCenter.getY() : 0.5;

            Range xRange = new Range(xMin, xMax).scale(xScale, xCenterRatio);
            Range yRange = new Range(yMin, yMax).scale(yScale, yCenterRatio);
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
            return new Viewport(x - w, x + w, yMin, yMax);
        }
    }

    public record Plot(String seriesName, @Nullable String unit, Color color,
                        List<@Nullable Number> data, Range dataRange) {

        public Plot(String header, @Nullable String unit, Color color, List<@Nullable Number> data) {
            this(header, unit, color, data, buildValueRange(data));
        }

        public static double getScaleFactor(double value) {
            int base = (int)Math.clamp(Math.floor(Math.log10(Math.abs(value))), 0, 3);
            return Math.pow(10, base);
        }

        public String style() {
            return "-fx-stroke: " + Views.toColorString(color) + ";" + "-fx-stroke-width: 0.6px;";
        }

        public double min() {
            return dataRange.getMin().doubleValue();
        }

        public double max() {
            return dataRange.getMax().doubleValue();
        }

        public Range valueRange() {
            double min = min();
            double max = max();
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

        private static Range buildValueRange(List<@Nullable Number> data) {
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
    }
}