package com.ugcs.geohammer.chart.tool;

import com.ugcs.geohammer.chart.Chart;
import com.ugcs.geohammer.chart.csv.SensorLineChart;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.format.svlog.SonarFile;
import com.ugcs.geohammer.model.SelectedTrace;
import com.ugcs.geohammer.model.TraceKey;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
import com.ugcs.geohammer.model.event.SeriesSelectedEvent;
import com.ugcs.geohammer.model.event.WhatChanged;
import com.ugcs.geohammer.model.template.DataMapping;
import com.ugcs.geohammer.model.template.Template;
import com.ugcs.geohammer.model.template.data.SensorData;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.math.PrefixSum;
import com.ugcs.geohammer.math.SegmentTree;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.util.Templates;
import com.ugcs.geohammer.view.ResourceImageHolder;
import com.ugcs.geohammer.view.Views;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import java.util.AbstractList;
import java.util.List;
import java.util.Objects;

@Component
public class StatisticsTool extends ToolView {

    private static final String NO_VALUE = "n/a";
    private static final String NO_SERIES = "No series";

    private final Model model;

    private final InspectorView inspectorView;

    private Label seriesName;

    private Label value;

    private MetricsView metricsView;

    private MetricsContextSelector metricsContextSelector;

    private SensorLineChart chart;

    private int chartDecimals = SensorData.DEFAULT_DECIMALS;

    public StatisticsTool(Model model, InspectorView inspectorView) {
        this.model = model;
        this.inspectorView = inspectorView;

        // name and value
        seriesName = new Label(NO_SERIES);
        seriesName.setStyle("-fx-font-weight: bold;-fx-font-size: 14px;");
        HBox.setHgrow(seriesName, Priority.ALWAYS);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        value = new Label(NO_VALUE);
        value.setStyle("-fx-font-weight: bold;-fx-font-size: 14px;");

        Button showInspector = Views.createSvgButton(
                ResourceImageHolder.LIST, Color.valueOf("#888888"), 20, "Inspect values");
        showInspector.setOnAction(e -> inspectorView.toggle());

        HBox valueGroup = new HBox(seriesName, spacer, value, showInspector);
        valueGroup.setSpacing(Tools.DEFAULT_SPACING);
        valueGroup.setAlignment(Pos.CENTER_LEFT);

        // metrics
        metricsView = new MetricsView();
        metricsContextSelector = new MetricsContextSelector();
        BorderPane metricsGroup = createMetricsGroup(metricsView, metricsContextSelector);

        VBox.setMargin(metricsGroup, new Insets(Tools.DEFAULT_SPACING, 0, 0, 0));

        VBox container = new VBox(Tools.DEFAULT_SPACING, valueGroup, metricsGroup);
        container.setPadding(Tools.DEFAULT_OPTIONS_INSETS);

        getChildren().add(container);

        // show by default
        show(true);
    }

    @Override
    public boolean isVisibleFor(SgyFile file) {
        return file instanceof CsvFile || file instanceof SonarFile;
    }

    private int getDecimals(SgyFile file, String header) {
        SensorData sensorData = getSensorDataByHeader(file, header);
        return sensorData != null
                ? sensorData.getDecimals()
                : SensorData.DEFAULT_DECIMALS;
    }

    private SensorData getSensorDataByHeader(SgyFile file, String header) {
        Template template = Templates.getTemplate(file);
        if (template == null) {
            return null;
        }
        DataMapping dataMapping = template.getDataMapping();
        if (dataMapping == null) {
            return null;
        }
        return dataMapping.getDataValueByHeader(header);
    }

    @Override
    public void updateView() {
        SgyFile file = selectedFile;
        Chart selectedChart = model.getChart(file);
        chart = selectedChart instanceof SensorLineChart sensorChart
                ? sensorChart
                : null;
        chartDecimals = file != null && chart != null
                ? getDecimals(file, chart.getSelectedSeriesName())
                : SensorData.DEFAULT_DECIMALS;

        if (chart == null) {
            seriesName.setText(NO_SERIES);
            value.setText(NO_VALUE);
        } else {
            String series = chart.getSelectedSeriesName();
            this.seriesName.setText(series);

            String valueText = NO_VALUE;
			SelectedTrace selectedTrace = model.getSelectedTrace(chart);
            TraceKey traceKey = selectedTrace != null ? selectedTrace.trace() : null;
            if (traceKey != null) {
                int index = traceKey.getIndex();
                List<GeoData> values = chart.getFile().getGeoData();
                Number nearestValue = findNearestValue(values, series, index);
                if (nearestValue != null) {
                    valueText = formatValue(nearestValue.doubleValue());
                    ColumnSchema schema = GeoData.getSchema(values);
                    String unit = schema != null ? schema.getColumnUnit(series) : null;
                    if (!Strings.isNullOrEmpty(unit)) {
                        valueText += " " + unit;
                    }
                }
            }
            value.setText(valueText);
        }

        if (metricsView != null) {
            metricsView.update();
        }
    }

    @Nullable
    private Number findNearestValue(List<GeoData> values, String header, int index) {
        if (values == null) {
            return null;
        }
        int size = values.size();
        if (values.isEmpty() || index < 0 || index >= size) {
            return null;
        }

        Number value = values.get(index) != null
                ? values.get(index).getNumber(header)
                : null;
        if (value != null) {
            return value;
        }

        int leftIndex = index - 1;
        int rightIndex = index + 1;
        while (leftIndex >= 0 || rightIndex < size) {
            if (leftIndex >= 0) {
                GeoData geoData = values.get(leftIndex);
                Number leftValue = geoData != null ? geoData.getNumber(header) : null;
                if (leftValue != null) {
                    return leftValue;
                }
                leftIndex--;
            }
            if (rightIndex < size) {
                GeoData geoData = values.get(rightIndex);
                Number rightValue = geoData != null ? geoData.getNumber(header) : null;
                if (rightValue != null) {
                    return rightValue;
                }
                rightIndex++;
            }
        }
        return null;
    }

    private String formatValue(double value) {
        if (Double.isNaN(value)) {
            return NO_VALUE;
        }
        return String.format("%." + chartDecimals + "f", value);
    }

    private BorderPane createMetricsGroup(MetricsView metricsView, MetricsContextSelector metricsContextSelector) {
        BorderPane container = new BorderPane();

        container.setCenter(metricsView);
        container.setBottom(metricsContextSelector);

        container.setStyle("-fx-border-color: #cccccc; -fx-border-width: 1px; -fx-border-radius: 6px;");

        BorderPane.setMargin(metricsView, new Insets(6));
        BorderPane.setMargin(metricsContextSelector, new Insets(6));

        return container;
    }

    @EventListener
    private void onFileSelected(FileSelectedEvent event) {
        Platform.runLater(() -> selectFile(event.getFile()));
    }

    @EventListener
    private void onSeriesSelected(SeriesSelectedEvent event) {
        if (Objects.equals(selectedFile, event.getFile())) {
            Platform.runLater(this::updateView);
        }
    }

    @EventListener
    private void onChange(WhatChanged changed) {
        if (changed.isCsvDataZoom() || changed.isTraceCut() || changed.isTraceSelected()) {
            Platform.runLater(this::updateView);
        }
    }

    enum MetricsContext {
        VISIBLE_AREA("Visible Area"),
        LINE("Line"),
        FILE("File");

        private final String name;

        MetricsContext(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    class MetricsView extends GridPane {
        Label count;
        Label min;
        Label max;
        Label avg;
        Label range;

        // index
        SensorLineChart indexedChart;
        String indexedSeries;
        SegmentTree minMaxIndex;
        PrefixSum avgIndex;

        public MetricsView() {
            Label countHeader = createHeaderLabel("count");
            Label minHeader = createHeaderLabel("min");
            Label maxHeader = createHeaderLabel("max");
            Label avgHeader = createHeaderLabel("average");
            Label rangeHeader = createHeaderLabel("range");

            count = createValueLabel(NO_VALUE);
            min = createValueLabel(NO_VALUE);
            max = createValueLabel(NO_VALUE);
            avg = createValueLabel(NO_VALUE);
            range = createValueLabel(NO_VALUE);

            add(countHeader, 0, 0);
            add(minHeader, 1, 0);
            add(maxHeader, 2, 0);
            add(avgHeader, 3, 0);
            add(rangeHeader, 4, 0);

            add(count, 0, 1);
            add(min, 1, 1);
            add(max, 2, 1);
            add(avg, 3, 1);
            add(range, 4, 1);

            for (int i = 0; i < 5; i++) {
                ColumnConstraints columnConstraints = new ColumnConstraints();
                columnConstraints.setPercentWidth(20);
                columnConstraints.setHalignment(HPos.CENTER);
                columnConstraints.setHgrow(Priority.ALWAYS);
                getColumnConstraints().add(columnConstraints);
            }

            setVgap(Tools.DEFAULT_SPACING);
            setMaxWidth(Double.MAX_VALUE);
        }

        Label createHeaderLabel(String text) {
            Label label = new Label(text);
            label.setStyle("-fx-font-weight: bold; -fx-font-size: 10px; -fx-text-fill: #888888;");
            return label;
        }

        Label createValueLabel(String text) {
			return new Label(text);
        }

        void updateIndex() {
            String series = chart != null
                    ? chart.getSelectedSeriesName()
                    : null;
            if (Objects.equals(indexedChart, chart)
                    && Objects.equals(indexedSeries, series)) {
                return;
            }

            if (chart == null || series == null) {
                minMaxIndex = null;
                avgIndex = null;
            } else {
                List<Double> values = new GeoDataAdapter(
                        chart.getFile().getGeoData(),
                        series
                );
                minMaxIndex = new SegmentTree(values);
                avgIndex = new PrefixSum(values);
            }

            indexedChart = chart;
            indexedSeries = series;
        }

        void update() {
            updateIndex();

            if (chart == null || chart.getSelectedSeriesName() == null) {
                count.setText(NO_VALUE);
                min.setText(NO_VALUE);
                max.setText(NO_VALUE);
                avg.setText(NO_VALUE);
                range.setText(NO_VALUE);
                return;
            }

            MetricsContext metricsContext = metricsContextSelector.getSelectedContext();
            IndexRange queryRange = getRange(chart, metricsContext);

            if (queryRange == null) {
                count.setText(NO_VALUE);
                min.setText(NO_VALUE);
                max.setText(NO_VALUE);
                avg.setText(NO_VALUE);
                range.setText(NO_VALUE);
                return;
            }

            int l = queryRange.from();
            int r = queryRange.to();

            int countValue = avgIndex.queryCount(l, r);
            double minValue = minMaxIndex.queryMin(l, r);
            double maxValue = minMaxIndex.queryMax(l, r);
            double avgValue = avgIndex.queryAvg(l, r);

            count.setText(Integer.toString(countValue));
            min.setText(formatValue(minValue));
            max.setText(formatValue(maxValue));
            avg.setText(formatValue(avgValue));
            range.setText(formatValue(maxValue - minValue));
        }

        @Nullable
        IndexRange getRange(SensorLineChart chart, MetricsContext metricsContext) {
            switch (metricsContext) {
                case VISIBLE_AREA:
                    return chart.getVisibleRange();
                case LINE:
					SelectedTrace selectedTrace = model.getSelectedTrace(chart);
                    TraceKey traceKey = selectedTrace != null ? selectedTrace.trace() : null;
                    if (traceKey != null) {
                        int selectedIndex = traceKey.getIndex();
                        int traceLineIndex = chart.getValueLineIndex(selectedIndex);
                        return chart.getFile().getLineRanges().get(traceLineIndex);
                    }
                    // no range if no trace is selected
                    return null;
                case FILE:
                    return new IndexRange(0, chart.numTraces());
                default:
                    return null;
            }
        }
    }

    class MetricsContextSelector extends HBox {

        ToggleGroup toggleGroup;

        MetricsContextSelector() {
            setAlignment(Pos.CENTER);
            setSpacing(1);

            toggleGroup = new ToggleGroup();

            for (MetricsContext context : MetricsContext.values()) {
                ToggleButton button = new ToggleButton(context.getName());
                button.setToggleGroup(toggleGroup);
                button.setUserData(context);
                boolean selected = context == MetricsContext.VISIBLE_AREA;
                button.setSelected(selected);
                setButtonStyle(button, selected);
                getChildren().add(button);
            }

            toggleGroup.selectedToggleProperty().addListener(this::onToggleChanged);
        }

        void onToggleChanged(ObservableValue<? extends Toggle> observable, Toggle oldValue, Toggle newValue) {
            if (oldValue != null) {
                ToggleButton oldButton = (ToggleButton)oldValue;
                setButtonStyle(oldButton, false);
            }
            if (newValue != null) {
                ToggleButton newButton = (ToggleButton) newValue;
                setButtonStyle(newButton, true);

                if (metricsView != null) {
                    metricsView.update();
                }
            } else if (oldValue != null) {
                ToggleButton oldButton = (ToggleButton)oldValue;
                oldButton.setSelected(true);
            }
        }

        void setButtonStyle(ToggleButton button, boolean selected) {
            String style = "-fx-font-size: 11px;-fx-padding: 3px 16px;";
            if (selected) {
                style += "-fx-background-color: #999999;-fx-text-fill: white;";
            } else {
                style += "-fx-background-color: #e3e3e3;-fx-text-fill: #666666;";
            }

            MetricsContext context = (MetricsContext) button.getUserData();
            MetricsContext[] values = MetricsContext.values();
            boolean isFirst = values.length > 0 && context == values[0];
            boolean isLast = values.length > 0 && context == values[values.length - 1];

            if (isFirst) {
                style += "-fx-background-radius: 6px 0 0 6px;";
            } else if (isLast) {
                style += "-fx-background-radius: 0 6px 6px 0;";
            } else {
                style += "-fx-background-radius: 0;";
            }

            button.setStyle(style);
        }

        MetricsContext getSelectedContext() {
            Object userData = toggleGroup.getSelectedToggle().getUserData();
            return userData instanceof MetricsContext metricsContext
                    ? metricsContext
                    : MetricsContext.FILE;
        }
    }

    static class GeoDataAdapter extends AbstractList<Double> {

        final List<GeoData> values;

        final String header;

        public GeoDataAdapter(List<GeoData> values, String header) {
            Check.notNull(values);
            Check.notNull(header);

            this.values = values;
            this.header = header;
        }

        @Override
        public Double get(int index) {
            GeoData value = values.get(index);
            Number number = value != null
                    ? value.getNumber(header)
                    : null;
            return number != null
                    ? number.doubleValue()
                    : Double.NaN;
        }

        @Override
        public int size() {
            return values.size();
        }
    }
}
