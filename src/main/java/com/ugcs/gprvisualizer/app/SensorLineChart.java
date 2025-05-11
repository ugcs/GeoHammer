package com.ugcs.gprvisualizer.app;

import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.github.thecoldwine.sigrun.common.ext.*;
import com.google.common.base.Strings;
import com.ugcs.gprvisualizer.app.auxcontrol.BaseObject;
import com.ugcs.gprvisualizer.app.auxcontrol.FoundPlace;
import com.ugcs.gprvisualizer.app.events.FileClosedEvent;
import com.ugcs.gprvisualizer.app.filter.MedianCorrectionFilter;
import com.ugcs.gprvisualizer.app.parcers.GeoCoordinates;
import com.ugcs.gprvisualizer.app.yaml.Template;
import com.ugcs.gprvisualizer.event.FileSelectedEvent;
import com.ugcs.gprvisualizer.event.WhatChanged;
import com.ugcs.gprvisualizer.utils.Check;
import com.ugcs.gprvisualizer.utils.Nodes;
import com.ugcs.gprvisualizer.utils.Range;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Side;
import javafx.scene.Cursor;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.Label;
import javafx.scene.control.skin.ComboBoxListViewSkin;
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
import org.springframework.util.StringUtils;

import com.ugcs.gprvisualizer.app.fir.FIRFilter;
import com.ugcs.gprvisualizer.app.parcers.GeoData;
import com.ugcs.gprvisualizer.app.parcers.SensorValue;
import com.ugcs.gprvisualizer.gpr.Model;
import com.ugcs.gprvisualizer.gpr.PrefSettings;

import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.Node;
import javafx.scene.chart.Axis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart.Data;
import javafx.scene.chart.XYChart.Series;

public class SensorLineChart extends Chart {

    private static final double ZOOM_STEP = 1.38;

    private static final String FILTERED_SERIES_SUFFIX = "_filtered";
    private static final String ANOMALY_SERIES_SUFFIX = "_anomaly";

    private static final Logger log = LoggerFactory.getLogger(SensorLineChart.class);

    private final ApplicationEventPublisher eventPublisher;
    private final PrefSettings settings;
    private Map<SeriesData, BooleanProperty> itemBooleanMap = new HashMap<>();
    @Nullable
    private LineChartWithMarkers lastLineChart = null;
    private Set<LineChartWithMarkers> charts = new HashSet<>();
    private Rectangle selectionRect = new Rectangle();
    private Label chartName;

    //private int scale;
    private CsvFile file;
    //private Node rootNode;

    private SortedMap<Integer, Range> lineRanges;

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private ScheduledFuture<?> updateTask;

    static {
        Runtime.getRuntime().addShutdownHook(
                new Thread(scheduler::shutdownNow));
    }

    public SensorLineChart(Model model, ApplicationEventPublisher eventPublisher, PrefSettings settings) {
        super(model);
        this.eventPublisher = eventPublisher;
        this.settings = settings;
    }

    private EventHandler<MouseEvent> mouseClickHandler = new EventHandler<MouseEvent>() {
        @Override
        public void handle(MouseEvent event) {
        	if (event.getClickCount() == 2) {
                if (event.getSource() instanceof LineChart lineChart) {
                    Axis<Number> xAxis = lineChart.getXAxis();
                    Point2D point = xAxis.screenToLocal(event.getScreenX(), event.getScreenY());
                    Number xValue = xAxis.getValueForDisplay(point.getX());

                    int traceIndex = xValue.intValue();
                    if (traceIndex >= 0 && traceIndex < file.getTraces().size()) {//model.getCsvTracesCount()) {
                        Trace trace = file.getTraces().get(traceIndex);
                        model.selectTrace(trace);
                        model.focusMapOnTrace(trace);
                    }
                }
                event.consume();
        	}
        }
	};

    public List<PlotData> generatePlotData(CsvFile csvFile) {

        List<GeoData> geoData = csvFile.getGeoData();

        Map<String, List<SensorValue>> sensorValues = new LinkedHashMap<>();
        geoData.forEach(data -> {
            data.getSensorValues().forEach(value -> {
                sensorValues.compute(value.semantic() + "--" + (StringUtils.hasText(value.units()) ? value.units() : " "), (k, v) -> {
                    if (v == null) {
                        v = new ArrayList<>();
                    }
                    v.add(value);
                    return v;
                });
            });
        });

        List<PlotData> plotDataList = new ArrayList<>();
        for (Map.Entry<String, List<SensorValue>> e: sensorValues.entrySet()) {
            var pd = e.getKey().split("--");
            var data = e.getValue().stream()
                    .map(SensorValue::data)
                    .collect(Collectors.toList());
            PlotData plotData = new PlotData(pd[0], pd[1], getColor(pd[0]), data);

            //calculateAverages(e.getValue().stream()
            //        .map(SensorValue::data)
            //        .collect(Collectors.toList()));
            plotDataList.add(plotData);
        }
        return plotDataList;
    }

    private Color getColor(String semantic) {
        return model.getColorBySemantic(semantic);
    }

    @Override
    public Node getRootNode() {
        return root;
    }

    @Override
    public void selectFile() {
        eventPublisher.publishEvent(new FileSelectedEvent(this, file));
    }

    public record PlotData(String semantic, String units, Color color, List<Number> data,
                           Set<Number> renderedIndices, boolean rendered) {

        public PlotData(String semantic, String units, Color color, List<Number> data) {
            this(semantic, units, color, data, new HashSet<>(), false);
        }

        public PlotData withData(List<Number> data) {
            return new PlotData(semantic, units, color, data);
        }

        /**
         * Set rendered flag to true
         * @return PlotData with rendered flag set to true
         */
        public PlotData render() {
            return new PlotData(semantic, units, color, data, renderedIndices, true);
        }

        public String getPlotStyle() {
            return "-fx-stroke: " + getColorString(color) + ";" + "-fx-stroke-width: 0.6px;";
        }

        public void setRendered(List<Data<Number, Number>> values) {
            if (values == null)
                return;
            values.forEach(v -> renderedIndices.add(v.getXValue()));
        }
    }

    private static String getColorString(Color color) {
        // Convert color to string in HEX format
        String colorString = String.format("#%02X%02X%02X",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
        return colorString;
    }

    record SeriesData(Series<Number, Number> series, Color color) {
        @Override
        public final String toString() {
            return series.getName();
        }
    }

    public BooleanProperty getItemBooleanProperty(SeriesData item) {
        return itemBooleanMap.get(item);
    }

    ComboBox<SeriesData> comboBox;
    ObservableList<SeriesData> seriesList;

    public VBox createChartWithMultipleYAxes(CsvFile file, List<PlotData> plotDataList) {

        this.file = file;
        this.lineRanges = file.getLineRanges();

        // Using StackPane to overlay charts
        this.chartsContainer = new StackPane();

        this.seriesList = FXCollections.observableArrayList();

        for (int i = 0; i < plotDataList.size(); i++) {
            var plotData = plotDataList.get(i);
            lastLineChart = createLineChart(plotData, i == 0);
        }

        // ComboBox with checkboxes
        comboBox = new ComboBox<>(seriesList) {
            @Override
            protected Skin<?> createDefaultSkin() {
                var skin = super.createDefaultSkin();
                ((ComboBoxListViewSkin) skin).setHideOnClick(false);
                return skin;
            }
        };

        comboBox.setValue(seriesList.isEmpty() ? null : seriesList.get(0));

        comboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            selectLineChart(newVal.series(), true);
            selectLineChart(oldVal.series(), false);
            selectFile();
        });

        comboBox.setValue(seriesList.isEmpty() ? null : getNonEmptySeries(seriesList));

        comboBox.setCellFactory(listView -> {
            return new CheckBoxListCell<>(this::getItemBooleanProperty) {
                @Override
                public void updateItem(SeriesData item, boolean empty) {
                    super.updateItem(item, empty);
                    this.setDisable(false); // it is required to fit the default state
                    if (item != null) {
                        if (item.series().getData().isEmpty()) {
                            this.setDisable(true);
                            this.setStyle("-fx-text-fill: gray;");
                        } else {
                            this.setStyle("-fx-text-fill: " + getColorString(item.color()) + ";");
                        }
                    } 
                }
            };
        });

        comboBox.setButtonCell(new ListCell<SeriesData>() {
            @Override
            protected void updateItem(SeriesData item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.series().getName());
                    setStyle("-fx-text-fill: " + getColorString(item.color()) + ";");
                }
            }
        });

        if (lastLineChart != null) {
            lastLineChart.setMouseTransparent(false);

            setSelectionHandlers(lastLineChart);
            setScrollHandlers(lastLineChart);

            lastLineChart.addEventHandler(MouseEvent.MOUSE_CLICKED, mouseClickHandler);

            lastLineChart.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
                log.debug("MouseClicked: " + event.getX() + ", " + event.getY());
            });

            charts.forEach(chart -> {
                if (GeoData.Semantic.LINE.getName().equals(chart.plotData.semantic)) {
                    int yValue = -1;
                    for (int i = 0; i < chart.plotData.data.size(); i++) {
                        if (chart.plotData.data.get(i) == null) {
                            continue;
                        }
                        var currentYValue = chart.plotData.data.get(i).intValue();
                        if (yValue != currentYValue) {
                            yValue = currentYValue;
                            Data<Number, Number> verticalMarker = new Data<>(i, 0);

                            Line line = new Line();
                            line.setStroke(chart.plotData.color);
                            line.setStrokeWidth(0.8);

                            Tooltip tooltip = new Tooltip("Remove Line " + currentYValue);
                            ImageView imageView = ResourceImageHolder.getImageView("closeFile.png");
                            Tooltip.install(imageView, tooltip);

                            imageView.setPickOnBounds(true);
                            imageView.addEventHandler(MouseEvent.MOUSE_ENTERED, event -> {
                                imageView.setCursor(Cursor.HAND);
                            });
                            imageView.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
                                System.out.println(event.getX() + ", " + event.getY() + ", " + currentYValue);

                                CsvFile copy = file.copy();
                                copy.setUnsaved(true);

                                List<GeoData> geoDataLinesList = new ArrayList<>();
                                List<Trace> traces = new ArrayList<>();
                                List<BaseObject> auxElements = new ArrayList<>();
                                for(GeoData geoData: file.getGeoData()) {
                                    if (geoData.getLine().data() != null && geoData.getLine().data().intValue() != currentYValue) {
                                        Trace trace = file.getTraces().get(geoData.getTraceNumber());
                                        traces.add(trace);
                                        GeoData gd = new GeoData(geoData);
                                        if (geoData.getLine().data().intValue() > currentYValue) {
                                            gd.setLineIndex(gd.getLine().data().intValue() - 1);
                                        }
                                        file.getAuxElements().stream().filter(FoundPlace.class::isInstance)
                                                .map(o -> ((FoundPlace) o))
                                                .filter(fp -> fp.getTraceInFile() == geoData.getTraceNumber())
                                                .forEach(fp -> auxElements.add(fp));
                                        geoDataLinesList.add(gd);
                                    }
                                }
                                copy.setTraces(traces);
                                copy.setGeoData(geoDataLinesList);
                                copy.setAuxElements(auxElements);
                                copy.updateTraces();

                                model.clearSelectedTrace(this);
                                model.getFileManager().removeFile(file);
                                model.getFileManager().addFile(copy);
                                model.updateChart(copy);

                                model.init();
                                model.initField();

                                eventPublisher.publishEvent(new WhatChanged(this, WhatChanged.Change.traceCut));
                            });

                            imageView.setTranslateX(1);
                            imageView.setTranslateY(1);

                            Pane pane = new Pane(imageView);
                            lastLineChart.addVerticalValueMarker(verticalMarker, line, null, pane, false);
                        }
                    }
                }
            });
        }

        file.getAuxElements().stream().map(o -> ((FoundPlace) o))
                .forEach(this::putFoundPlace);

        ImageView close = ResourceImageHolder.getImageView("close.png");
        close.setPickOnBounds(true);
        close.addEventHandler(MouseEvent.MOUSE_ENTERED, event -> {
            close.setCursor(Cursor.HAND);
        });
        close.setOnMouseClicked(event -> {
            close();
        });

        chartName = new Label();
        chartName.setFont(Font.font("Verdana", FontWeight.BOLD, 8));
        chartName.setTextFill(Color.rgb(60, 60, 60));
        updateChartName();

        Region space = new Region();
        HBox.setHgrow(space, Priority.ALWAYS);

        HBox top = new HBox(close, chartName, space, comboBox);
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

        //this.rootNode = root;

        //root.getChildren().add(0, profileScroll);

        getProfileScroll().setChangeListener((observable, oldVal, newVal) -> {
            zoomToProfileScroll();
            updateOnZoom(true);
        });
        updateProfileScroll();

        return root;
    }

    private LineChartWithMarkers createLineChart(PlotData plotData, boolean primary) {
        var data = plotData.data();

        // X-axis, common for all charts
        NumberAxis xAxis = getXAxis(data.size() / 10);

        // Y-axis
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel(plotData.units());
        yAxis.setSide(Side.RIGHT); // Y-axis on the right
        yAxis.setMinorTickVisible(false);

        Range valueRange = getValueRange(data, plotData.semantic);
        yAxis.setTickUnit(valueRange.getWidth() / 10);
        yAxis.setPrefWidth(70);
        if (!primary) {
            yAxis.setOpacity(0);
        }

        // Creating chart
        ZoomRect outZoomRect = new ZoomRect(0, Math.max(0, data.size() - 1),
                valueRange.getMin().doubleValue(), valueRange.getMax().doubleValue());
        LineChartWithMarkers lineChart = new LineChartWithMarkers(xAxis, yAxis, outZoomRect, plotData);
        lineChart.resetZoomRect();

        lineChart.setLegendVisible(false); // Hide legend
        lineChart.setCreateSymbols(false); // Disable symbols
        if (!primary) {
            lineChart.setVerticalGridLinesVisible(false);
            lineChart.setHorizontalGridLinesVisible(false);
            lineChart.setHorizontalZeroLineVisible(false);
            lineChart.setVerticalZeroLineVisible(false);
            lineChart.lookup(".chart-plot-background").setStyle("-fx-background-color: transparent;");
        }

        Series<Number, Number> series = new Series<>();
        series.setName(plotData.semantic());

        Series<Number, Number> filtered = new Series<>();
        filtered.setName(plotData.semantic() + FILTERED_SERIES_SUFFIX);

        // Add data to chart
        if (!data.isEmpty()) {
            addSeriesDataFiltered(series, plotData, 0, data.size());
        }

        SeriesData item = new SeriesData(series, plotData.color());
        seriesList.add(item);

        itemBooleanMap.put(item, createBooleanProperty(item));

        if (!item.series.getData().isEmpty()) {
            charts.add(lineChart);
            // Set random color for series
            lineChart.getData().add(item.series());
            setStyleForSeries(item.series(), plotData.getPlotStyle());

            lineChart.getData().add(filtered);
            setStyleForSeries(filtered, plotData.getPlotStyle());

            lineChart.getData().forEach(s -> {
                if (s.getNode() != null) {
                    s.getNode().setVisible(itemBooleanMap.get(item).get());
                }
            });
            // Add chart to container
            chartsContainer.getChildren().add(lineChart);
        }

        lineChart.setMouseTransparent(true);
        return lineChart;
    }

    public void updateChartName() {
        String fileName = (file.isUnsaved() ? "*" : "") + file.getFile().getName();
        chartName.setText(fileName);
    }

    private Map<FoundPlace, Data<Number, Number>> foundPlaces = new HashMap<>();

    private void putFoundPlace(FoundPlace fp) {
        Data<Number, Number> verticalMarker = new Data<>(fp.getTraceInFile(), 0);

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

    @Override
    public int getVisibleNumberOfTrace() {
        String seriesName = getSelectedSeriesName();
        LineChartWithMarkers selectedChart = getCharts().get(seriesName);
        if (selectedChart == null) {
            return 0;
        }
        ZoomRect zoomRect = selectedChart.zoomRect;
        return zoomRect.xMax.intValue() - zoomRect.xMin.intValue() + 1;
    }

    @Override
    public int getTracesCount() {
        String seriesName = getSelectedSeriesName();
        LineChartWithMarkers selectedChart = getCharts().get(seriesName);
        if (selectedChart == null) {
            return 0;
        }
        ZoomRect zoomRect = selectedChart.outZoomRect;
        return zoomRect.xMax.intValue() - zoomRect.xMin.intValue() + 1;
    }

    private List<Data<Number, Number>> getSubsampleInRange(List<Number> data, int lowerIndex, int upperIndex,
            Set<Number> filter) {
        // Validate indices
        if (lowerIndex < 0 || upperIndex > data.size() || lowerIndex >= upperIndex) {
            throw new IllegalArgumentException("Invalid range specified.");
        }

        // Calculate the number of points to sample within the specified range
        int range = upperIndex - lowerIndex;
        int desiredNumberOfPoints = 2000;
        int actualNumberOfPoints = Math.min(desiredNumberOfPoints, range);

        List<Data<Number, Number>> subsample = new ArrayList<>(actualNumberOfPoints);
        int step = Math.max(1, range / actualNumberOfPoints);
        for (int i = lowerIndex; i < upperIndex; i += step) {
            Number key = i;
            if (filter != null && filter.contains(key)) {
                continue;
            }
            Number value = data.get(i);
            if (value != null) {
                subsample.add(new Data<>(key, value));
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

    private int getValueLineIndex(int index) {
        List<GeoData> values = file.getGeoData();
        if (values == null || values.isEmpty()) {
            return 0;
        }
        // correct out of range values to point to the first or last trace
        index = Math.max(0, Math.min(index, values.size() - 1));
        return values.get(index).getLineIndex();
    }

    private int getViewLineIndex() {
        String seriesName = getSelectedSeriesName();
        LineChartWithMarkers selectedChart = getCharts().get(seriesName);
        if (selectedChart != null) {
            NumberAxis xAxis = (NumberAxis) selectedChart.getXAxis();
            int xCenter = (int) (0.5 * (xAxis.getLowerBound() + xAxis.getUpperBound()));
            return getValueLineIndex(xCenter);
        }
        // default: first range key or 0
        return !lineRanges.isEmpty() ? lineRanges.firstKey() : 0;
    }

    private void updateProfileScroll() {
        String seriesName = getSelectedSeriesName();
        LineChartWithMarkers selectedChart = getCharts().get(seriesName);
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
        for (LineChartWithMarkers chart : charts) {
            ZoomRect zoomRect = chart.createZoomRectForXRange(range, true);
            chart.setZoomRect(zoomRect);
        }
    }

    private void zoomToLine(int lineIndex) {
        Range range = lineRanges.get(lineIndex);
        for (LineChartWithMarkers chart : charts) {
            ZoomRect zoomRect = chart.createZoomRectForXRange(range);
            chart.setZoomRect(zoomRect);
        }
    }

    public void zoomToCurrentLine() {
        int lineIndex;
        if (currentVerticalMarker != null) {
            lineIndex = getValueLineIndex(currentVerticalMarker.getXValue().intValue());
        } else {
            lineIndex = getViewLineIndex();
        }
        zoomToLine(lineIndex);
        updateOnZoom(false);
    }

    public void zoomToPreviousLine() {
        int lineIndex = getViewLineIndex();
        int firstLineIndex = !lineRanges.isEmpty() ? lineRanges.firstKey() : 0;
        zoomToLine(Math.max(lineIndex - 1, firstLineIndex));
        updateOnZoom(false);
    }

    public void zoomToNextLine() {
        int lineIndex = getViewLineIndex();
        int lastLineIndex = !lineRanges.isEmpty() ? lineRanges.lastKey() : 0;
        zoomToLine(Math.min(lineIndex + 1, lastLineIndex));
        updateOnZoom(false);
    }

    /**
     * Zoom to full range
     */
    public void zoomToFit() {
        for (LineChartWithMarkers chart : charts) {
            chart.resetZoomRect();
        }
        updateOnZoom(false);
    }

    public void zoomIn() {
        double scale = 1.0 / ZOOM_STEP;
        zoom(scale, 1.0, null);
        updateOnZoom(false);
    }

    public void zoomOut() {
        double scale = ZOOM_STEP;
        zoom(scale, 1.0, null);
        updateOnZoom(false);
    }

    private void zoomToArea(Point2D start, Point2D end) {
        for (LineChartWithMarkers chart : charts) {
            ZoomRect zoomRect = chart.createZoomRectForArea(start, end);
            chart.setZoomRect(zoomRect);
        }
    }

    private void zoom(double scaleX, double scaleY, Point2D scaleCenter) {
        for (LineChartWithMarkers chart : charts) {
            ZoomRect zoomRect = chart.scaleZoomRect(chart.zoomRect, scaleX, scaleY, scaleCenter);
            chart.setZoomRect(zoomRect);
        }
    }

    private void updateOnZoom(boolean delayed) {
        updateProfileScroll();
        if (delayed) {
            scheduleUpdate(() -> {
                Platform.runLater(this::updateChartData);
            }, 300);
        } else {
            Platform.runLater(this::updateChartData);
        }
    }

    private void updateChartData() {
        for (LineChartWithMarkers chart : charts) {
            chart.updateLineChartData();
        }
    }

    private boolean confirmUnsavedChanges() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Warning");
        alert.setHeaderText("Current file is not saved. Continue?");
        alert.getButtonTypes().setAll(
                ButtonType.CANCEL,
                ButtonType.OK);

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get().equals(ButtonType.OK);
    }

    /**
     * Close chart
     */
    private void close() {
        if (file.isUnsaved() && !confirmUnsavedChanges()) {
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
                model.getFileManager().removeFile(file);
                model.removeChart(file);
                model.initField();
                model.init();
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

    private SeriesData getNonEmptySeries(ObservableList<SeriesData> seriesList) {
        return seriesList.stream().filter(s -> !s.series().getData().isEmpty()).findFirst().orElse(null);
    }

    private Data<Number, Number> currentVerticalMarker = null;
    private VBox root;
    private StackPane chartsContainer;

    /** 
     * Remove vertical marker 
     */
    public void removeVerticalMarker() {
        if (lastLineChart != null && currentVerticalMarker != null) {
            lastLineChart.removeVerticalValueMarker(currentVerticalMarker);
            currentVerticalMarker = null;
        }
    }

    /**
     * Add vertical marker
     * @param x
     */
    public void putVerticalMarker(int x) {
        if (lastLineChart != null) {
            if (currentVerticalMarker != null) {
                lastLineChart.removeVerticalValueMarker(currentVerticalMarker);
            }
            currentVerticalMarker = new Data<>(x, 0);
            lastLineChart.addVerticalValueMarker(currentVerticalMarker);
        }
    }

    private NumberAxis getXAxis(int tickUnit) {
        NumberAxis xAxis = new NumberAxis();
        //xAxis.setLabel("Common X Axis");
        xAxis.setMinorTickVisible(false);
        xAxis.setTickMarkVisible(false);
        xAxis.setTickLabelsVisible(false);
        //xAxis.setAutoRanging(false);
        //xAxis.setUpperBound(2000);
        xAxis.setTickUnit(tickUnit);
        return xAxis;
    }

    private Map<String, Double> semanticMinValues = new HashMap<>();
    private Map<String, Double> semanticMaxValues = new HashMap<>();

    public Double getSemanticMinValue() {
        return semanticMinValues.getOrDefault(getSelectedSeriesName(), 0.0);
    }

    public Double getSemanticMaxValue() {
        return semanticMaxValues.getOrDefault(getSelectedSeriesName(), 0.0);
    }

    private double getScaleFactor(double value) {
        int base = (int)Math.clamp(Math.floor(Math.log10(Math.abs(value))), 0, 3);
        return Math.pow(10, base);
    }

    private Range getValueRange(List<Number> data, String semantic) {
        List<Double> sorted = data.stream().filter(Objects::nonNull)
                .mapToDouble(Number::doubleValue)
                .sorted()
                .boxed()
                .toList();
        int offset = (int)(0.01 * sorted.size());
        double min = !sorted.isEmpty()
                ? sorted.get(offset)
                : 0.0;
        double max = !sorted.isEmpty()
                ? sorted.get(sorted.size() - 1 - offset)
                : 0.0;

        semanticMinValues.put(semantic, min);
        semanticMaxValues.put(semantic, max);

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

    private BooleanProperty createBooleanProperty(SeriesData item) {
        var settingsValue = settings.getSetting(file.getParser().getTemplate().getName() + "." + item.series, "visible");
        boolean initialValue = !item.series().getData().isEmpty() && (settingsValue == null || settingsValue.equals("true"));
        final BooleanProperty booleanProperty = new SimpleBooleanProperty(item, "visible", initialValue);
        booleanProperty.addListener((observable, oldValue, newValue) -> {
            item.series().getChart().getData().forEach(series -> {
                if (series.getNode() != null) {
                    settings.saveSetting(file.getParser().getTemplate().getName() + "." + item.series, Map.of("visible", newValue));
                    series.getNode().setVisible(newValue);
                }
            });
        });
        return booleanProperty;
    }

    private void selectLineChart(Series<Number, Number> series, boolean isVisible) {
        LineChart<Number, Number> lineChart = (LineChart) series.getChart();

        lineChart.setVerticalGridLinesVisible(isVisible);
        lineChart.setHorizontalGridLinesVisible(isVisible);
        lineChart.setHorizontalZeroLineVisible(isVisible);
        lineChart.setVerticalZeroLineVisible(isVisible);

        lineChart.getYAxis().setOpacity(isVisible ? 1: 0);
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
            Range newYRange = getValueRange(plotData.data, plotData.semantic);

            NumberAxis yAxis = (NumberAxis)getYAxis();
            yAxis.setTickUnit(newYRange.getWidth() / 10);

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

            NumberAxis xAxis = (NumberAxis)getXAxis();
            Number xMin = xAxis.getValueForDisplay(plotStart.getX());
            Number xMax = xAxis.getValueForDisplay(plotEnd.getX());

            NumberAxis yAxis = (NumberAxis)getYAxis();
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
            NumberAxis xAxis = (NumberAxis)getXAxis();
            xAxis.setAutoRanging(false);
            xAxis.setLowerBound(zoomRect.xMin.doubleValue());
            xAxis.setUpperBound(zoomRect.xMax.doubleValue());

            NumberAxis yAxis = (NumberAxis) getYAxis();
            yAxis.setAutoRanging(false);
            yAxis.setLowerBound(zoomRect.yMin.doubleValue());
            yAxis.setUpperBound(zoomRect.yMax.doubleValue());
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

            // Создаем контейнер для изображений/флагов
            VBox imageContainer = new VBox();
            imageContainer.setAlignment(Pos.TOP_CENTER);

            if (imageView != null) {
                imageContainer.getChildren().add(imageView);
            }

            if (flag != null) {
                imageContainer.getChildren().add(flag);
            }

            // Добавляем контейнер с изображениями
            markerBox.getChildren().add(imageContainer);

            // Добавляем линию
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

                // Получаем контейнер с изображениями (первый элемент)
                VBox imageContainer = (VBox) markerBox.getChildren().get(0);
                double imageHeight = imageContainer.getChildren().stream()
                    .mapToDouble(node -> node.getBoundsInLocal().getHeight())
                    .sum();

                // Получаем линию (второй элемент)
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

        Platform.runLater(() -> {
            chart.updateLineChartData();
        });
    }

    public void lowPassFilter(String seriesName, int lowPassOrder) {
        LineChartWithMarkers chart = getDataChart(seriesName);
        if (chart == null) {
            return;
        }

        chart.setFilterOptions(chart.getFilterOptions().withLowPassOrder(lowPassOrder));
        applyFilters(chart);

        Platform.runLater(() -> {
            chart.updateLineChartData();
        });
    }

    private void applyFilters(LineChartWithMarkers chart) {
        Check.notNull(chart);
        FilterOptions filterOptions = chart.getFilterOptions();

        if (!filterOptions.hasAny()) {
            // undo all
            chart.filteredData = null;
            for (int i = 0; i < file.getGeoData().size(); i++) {
                file.getGeoData().get(i).undoSensorValue(chart.plotData.semantic);
            }
            return;
        }

        // shared by all lines
        double lowPassSamplingRate = filterOptions.lowPassOrder() != 0
                ? getLowPassSamplingRate()
                : 0.0;

        List<Number> values = chart.plotData.data;
        List<Number> filtered = new ArrayList<>(values.size());
        for (Range range : lineRanges.values()) {
            int from = range.getMin().intValue();
            int to = range.getMax().intValue();

            List<Number> rangeValues = values.subList(from, to + 1);
            // low-pass
            try {
                if (filterOptions.lowPassOrder() != 0) {
                    rangeValues = lowPass(rangeValues,
                            filterOptions.lowPassOrder(),
                            lowPassSamplingRate);
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

        assert filtered.size() == file.getTraces().size();
        chart.filteredData = chart.plotData.withData(filtered);
        for (int i = 0; i < file.getGeoData().size(); i++) {
            file.getGeoData().get(i).setSensorValue(chart.plotData.semantic, filtered.get(i));
        }
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

    private List<Number> lowPass(List<Number> values, int order, double samplingRate) {
        if (values == null || values.isEmpty()) {
            return values;
        }

        // data size should be >= 2 * filterOrder + 1
        // min filter order = (size - 1) / 2
        order = Math.min(order, (values.size() - 1) / 2);
        int shift = order / 2;
        double cutoffFrequency = samplingRate / order;
        FIRFilter filter = new FIRFilter(order, cutoffFrequency, samplingRate);

        var filtered = filter.filterList(values).subList(shift, values.size() + shift);
        assert filtered.size() == values.size();

        double rmsOriginal = calculateRMS(values.stream()
                .filter(Objects::nonNull)
                .mapToDouble(Number::doubleValue)
                .toArray());
        double rmsFiltered = calculateRMS(filtered.stream()
                .filter(Objects::nonNull)
                .mapToDouble(Number::doubleValue)
                .toArray());

        double scale = rmsOriginal / rmsFiltered;
        for (int i = 0; i < filtered.size(); i++) {
            Number value = filtered.get(i);
            if (value != null) {
                filtered.set(i, scale * value.doubleValue());
            }
        }
        return filtered;
    }

    private double getLowPassSamplingRate() {
        int seriesLimit = 2000;
        if (!lineRanges.isEmpty()) {
            Range firstRange = lineRanges.firstEntry().getValue();
            seriesLimit = Math.min(seriesLimit, (int)firstRange.getWidth());
            seriesLimit = Math.max(seriesLimit, 2);
        }
        List<Long> timestamps = file.getGeoData().stream()
                .limit(seriesLimit)
                .map(GeoCoordinates::getDateTime)
                .map(dt -> dt.toInstant(ZoneOffset.UTC).toEpochMilli())
                .collect(Collectors.toList());

        return FIRFilter.calculateSamplingRate(timestamps);
    }

    private static double calculateRMS(double[] signal) {
        double sum = 0.0;
        for (double v : signal) {
            sum += v * v;
        }
        return Math.sqrt(sum / signal.length);
    }

    public void medianCorrection(String seriesName, int window) {
        if (Strings.isNullOrEmpty(seriesName)) {
            return;
        }
        if (seriesName.endsWith(ANOMALY_SERIES_SUFFIX)) {
            seriesName = seriesName.substring(0,
                    seriesName.length() - ANOMALY_SERIES_SUFFIX.length());
        }
        LineChartWithMarkers chart = getDataChart(seriesName);
        if (chart == null) {
            return;
        }

        String filteredSeriesName = chart.plotData.semantic + ANOMALY_SERIES_SUFFIX;
        Template template = file.getParser().getTemplate();
        boolean hasAnomalySemantic = template.getDataMapping().getDataValues().stream()
                .anyMatch(v -> v != null && Objects.equals(v.getSemantic(), filteredSeriesName));
        if (!hasAnomalySemantic) {
            String errorMessage = """
                    Cannot apply running median filter to "%s" \
                    because there is no "%s" field \
                    defined in import template.
                    """.formatted(seriesName, filteredSeriesName);
            MessageBoxHelper.showError(errorMessage, "");
            return;
        }

        PlotData data = chart.filteredData != null && !chart.filteredData.data().isEmpty()
                ? chart.filteredData
                : chart.plotData;

        List<Number> values = data.data;
        List<Number> filtered = new ArrayList<>(values.size());
        for (Range range : lineRanges.values()) {
            int from = range.getMin().intValue();
            int to = range.getMax().intValue();

            List<Number> rangeValues = values.subList(from, to + 1);
            MedianCorrectionFilter medianCorrection = new MedianCorrectionFilter(window);
            rangeValues = medianCorrection.apply(rangeValues);

            // put all back
            filtered.addAll(rangeValues);
        }

        assert filtered.size() == file.getTraces().size();
        for (int i = 0; i < file.getGeoData().size(); i++) {
            file.getGeoData().get(i).setSensorValue(filteredSeriesName, filtered.get(i));
        }
        file.setUnsaved(true);

        Platform.runLater(() -> {
            LineChartWithMarkers filteredChart = getCharts().get(filteredSeriesName);
            if (filteredChart == null) {
                PlotData filteredData = new PlotData(
                        filteredSeriesName,
                        chart.plotData.units,
                        chart.plotData.color,
                        filtered
                );
                createLineChart(filteredData, false);
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
        return charts.stream().collect(Collectors.toMap(c -> c.plotData.semantic, c -> c));
    }

    private LineChartWithMarkers getDataChart(String seriesName) {
        // search chart by series name (excluding line markers)
        if (seriesName == null) {
            return null;
        }
        if (seriesName.equals(GeoData.Semantic.LINE.getName())) {
            return null;
        }
        return charts.stream()
                .filter(c -> seriesName.equals(c.plotData.semantic))
                .findFirst()
                .orElse(null);
    }

    public boolean isSameTemplate(CsvFile selectedFile) {
        return file.isSameTemplate(selectedFile);
    }

    public String getSelectedSeriesName() {
        return comboBox != null
                ? comboBox.getValue().series.getName()
                : null;
    }

    @Override
    public List<SgyFile> getFiles() {
        return List.of(file);
    }

    @Override
    public void selectTrace(Trace trace, boolean focus) {
        if (trace == null) {
            // clear selection
            removeVerticalMarker();
            return;
        }

        // TODO focus does not affect behavior

        int selectedX = trace.getIndexInFile();
        NumberAxis xAxis = (NumberAxis) lastLineChart.getXAxis();
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

            for (LineChartWithMarkers chart: charts) {
                var yAxis = (NumberAxis) chart.getYAxis();

                ZoomRect zoomRect = new ZoomRect(lowerIndex, upperIndex, yAxis.getLowerBound(), yAxis.getUpperBound());
                chart.setZoomRect(zoomRect);
                chart.updateLineChartData();
            }
        }
        putVerticalMarker(selectedX);
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
            removeVerticalMarker();
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