package com.ugcs.gprvisualizer.app;

import com.github.thecoldwine.sigrun.common.ext.CsvFile;
import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.github.thecoldwine.sigrun.common.ext.TraceKey;
import com.ugcs.gprvisualizer.app.parcers.GeoData;
import com.ugcs.gprvisualizer.app.parcers.SensorValue;
import com.ugcs.gprvisualizer.app.parcers.csv.CsvParser;
import com.ugcs.gprvisualizer.app.yaml.DataMapping;
import com.ugcs.gprvisualizer.app.yaml.Template;
import com.ugcs.gprvisualizer.app.yaml.data.SensorData;
import com.ugcs.gprvisualizer.gpr.Model;
import com.ugcs.gprvisualizer.math.PrefixSum;
import com.ugcs.gprvisualizer.math.SegmentTree;
import com.ugcs.gprvisualizer.utils.Check;
import com.ugcs.gprvisualizer.utils.Nulls;
import com.ugcs.gprvisualizer.utils.Range;
import com.ugcs.gprvisualizer.utils.Strings;
import javafx.beans.value.ObservableValue;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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

import java.util.AbstractList;
import java.util.List;
import java.util.Objects;

public class StatisticsView extends VBox {

    private static final String NO_VALUE = "n/a";
    private static final String NO_SERIES = "No series";

    private final Model model;

    private Label seriesName;

    private Label value;

    private Label total;

    private MetricsView metricsView;

    private MetricsContextSelector metricsContextSelector;

    private SensorLineChart chart;

    private int chartDecimals = SensorData.DEFAULT_DECIMALS;

    public StatisticsView(Model model) {
        this.model = model;

        setSpacing(OptionPane.DEFAULT_SPACING);
        setPadding(OptionPane.DEFAULT_OPTIONS_INSETS);

        // name and value
        seriesName = new Label(NO_SERIES);
        seriesName.setStyle("-fx-font-weight: bold;-fx-font-size: 14px;");
        HBox.setHgrow(seriesName, Priority.ALWAYS);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        value = new Label(NO_VALUE);
        value.setStyle("-fx-font-weight: bold;-fx-font-size: 14px;");

        HBox valueGroup = new HBox(seriesName, spacer, value);
        valueGroup.setSpacing(OptionPane.DEFAULT_SPACING);

        // total
        total = new Label(Strings.empty());

        // metrics
        metricsView = new MetricsView();
        metricsContextSelector = new MetricsContextSelector();
        BorderPane metricsGroup = createMetricsGroup(metricsView, metricsContextSelector);

        VBox.setMargin(metricsGroup, new Insets(OptionPane.DEFAULT_SPACING, 0, 0, 0));

        getChildren().addAll(
                valueGroup,
                total,
                metricsGroup);

        setVisible(false);
        setManaged(false);
    }

    private int getDecimals(CsvFile csvFile, String semantic) {
        SensorData sensorData = getSensorDataBySemantic(csvFile, semantic);
        return sensorData != null
                ? sensorData.getDecimals()
                : SensorData.DEFAULT_DECIMALS;
    }

    private SensorData getSensorDataBySemantic(CsvFile csvFile, String semantic) {
        if (csvFile == null) {
            return null;
        }
        CsvParser parser = csvFile.getParser();
        if (parser == null) {
            return null;
        }
        Template template = parser.getTemplate();
        if (template == null) {
            return null;
        }
        DataMapping dataMapping = template.getDataMapping();
        if (dataMapping == null) {
            return null;
        }
        for (SensorData sensorData : Nulls.toEmpty(dataMapping.getDataValues())) {
            if (Objects.equals(semantic, sensorData.getSemantic())) {
                return sensorData;
            }
        }
        return null;
    }

    public void update(SgyFile selectedFile) {
        chartDecimals = SensorData.DEFAULT_DECIMALS;
        if (selectedFile instanceof CsvFile csvFile) {
            chart = model.getCsvChart(csvFile).orElse(null);
            if (chart != null) {
                chartDecimals = getDecimals(csvFile, chart.getSelectedSeriesName());
            }
        } else {
            chart = null;
        }

        if (chart == null) {
            seriesName.setText(NO_SERIES);
            value.setText(NO_VALUE);
            total.setText(Strings.empty());
        } else {
            String series = chart.getSelectedSeriesName();
            this.seriesName.setText(series);

            List<GeoData> values = chart.getFile().getGeoData();
            int numValues = values.size();
            String totalText = numValues + " total value";
            if (numValues % 10 != 1 || numValues % 100 == 11) {
                totalText += "s";
            }
            this.total.setText(totalText);

            String valueText = NO_VALUE;
            TraceKey selectedTrace = model.getSelectedTrace(chart);
            if (selectedTrace != null) {
                GeoData geoData = values.get(selectedTrace.getIndex());
                if (geoData != null) {
                    SensorValue sensorValue = geoData.getSensorValue(series);
                    if (sensorValue != null && sensorValue.data() != null) {
                        valueText = formatValue(sensorValue.data().doubleValue());
                        if (!Strings.isNullOrEmpty(sensorValue.units())) {
                            valueText += " " + sensorValue.units();
                        }
                    }
                }
            }
            value.setText(valueText);
        }

        if (metricsView != null) {
            metricsView.update();
        }
    }

    private String formatValue(double value) {
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
            Label minHeader = createHeaderLabel("min");
            Label maxHeader = createHeaderLabel("max");
            Label avgHeader = createHeaderLabel("average");
            Label rangeHeader = createHeaderLabel("range");

            min = createValueLabel(NO_VALUE);
            max = createValueLabel(NO_VALUE);
            avg = createValueLabel(NO_VALUE);
            range = createValueLabel(NO_VALUE);

            add(minHeader, 0, 0);
            add(maxHeader, 1, 0);
            add(avgHeader, 2, 0);
            add(rangeHeader, 3, 0);

            add(min, 0, 1);
            add(max, 1, 1);
            add(avg, 2, 1);
            add(range, 3, 1);

            for (int i = 0; i < 4; i++) {
                ColumnConstraints columnConstraints = new ColumnConstraints();
                columnConstraints.setPercentWidth(25);
                columnConstraints.setHalignment(HPos.CENTER);
                columnConstraints.setHgrow(Priority.ALWAYS);
                getColumnConstraints().add(columnConstraints);
            }

            setVgap(OptionPane.DEFAULT_SPACING);
            setMaxWidth(Double.MAX_VALUE);
        }

        Label createHeaderLabel(String text) {
            Label label = new Label(text);
            label.setStyle("-fx-font-weight: bold; -fx-font-size: 10px; -fx-text-fill: #888888;");
            return label;
        }

        Label createValueLabel(String text) {
            Label label = new Label(text);
            return label;
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

            if (chart == null) {
                min.setText(NO_VALUE);
                max.setText(NO_VALUE);
                avg.setText(NO_VALUE);
                range.setText(NO_VALUE);
                return;
            }

            MetricsContext metricsContext = metricsContextSelector.getSelectedContext();
            Range queryRange = getRange(chart, metricsContext);

            int l = queryRange.getMin().intValue();
            int r = queryRange.getMax().intValue() + 1;

            double minValue = minMaxIndex.queryMin(l, r);
            double maxValue = minMaxIndex.queryMax(l, r);
            double avgValue = avgIndex.queryAvg(l, r);

            min.setText(formatValue(minValue));
            max.setText(formatValue(maxValue));
            avg.setText(formatValue(avgValue));
            range.setText(formatValue(maxValue - minValue));
        }

        Range getRange(SensorLineChart chart, MetricsContext metricsContext) {
            switch (metricsContext) {
                case VISIBLE_AREA:
                    return chart.getVisibleXRange();
                case LINE:
                    int lineIndex = chart.getViewLineIndex();
                    return chart.getFile().getLineRanges().get(lineIndex);
                case FILE:
                    return new Range(0, chart.getFile().numTraces() - 1);
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

    class GeoDataAdapter extends AbstractList<Double> {

        final List<GeoData> values;

        final String semantic;

        public GeoDataAdapter(List<GeoData> values, String semantic) {
            Check.notNull(values);
            Check.notNull(semantic);

            this.values = values;
            this.semantic = semantic;
        }

        @Override
        public Double get(int index) {
            GeoData value = values.get(index);
            return value != null
                    ? value.getDouble(semantic).orElse(0.0)
                    : 0.0;
        }

        @Override
        public int size() {
            return values.size();
        }
    }
}
