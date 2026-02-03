package com.ugcs.geohammer.chart.tool;

import com.ugcs.geohammer.AppContext;
import com.ugcs.geohammer.chart.Chart;
import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.model.Column;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.TraceKey;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
import com.ugcs.geohammer.model.event.FileUpdatedEvent;
import com.ugcs.geohammer.model.event.SeriesUpdatedEvent;
import com.ugcs.geohammer.model.event.WhatChanged;
import com.ugcs.geohammer.model.template.DataMapping;
import com.ugcs.geohammer.model.template.Template;
import com.ugcs.geohammer.model.template.data.SensorData;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.util.Templates;
import com.ugcs.geohammer.view.ResourceImageHolder;
import com.ugcs.geohammer.view.Views;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class InspectorView {

    private static final String WINDOW_TITLE = "Inspector";

    private static final String NO_MEASUREMENT = "No measurement selected";

    private static final String NO_VALUE = "n/a";

    private final Model model;

    @Nullable
    private SgyFile selectedFile;

    private List<Value> values = List.of();

    // view
    @Nullable
    private Stage window;

    private Label title;

    private Button previousButton;

    private Button nextButton;

    private VBox valueContainer;

    public InspectorView(Model model) {
        this.model = model;
    }

    private Stage createWindow() {
        Stage window = new Stage();
        window.setTitle(WINDOW_TITLE);
        window.initOwner(AppContext.stage);
        window.initStyle(StageStyle.UTILITY);
        window.setResizable(true);
        window.setMinWidth(200);
        window.setMinHeight(250);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        HBox header = createHeader();
        root.setTop(header);

        valueContainer = new VBox();
        ScrollPane scrollPane = new ScrollPane(valueContainer);
        scrollPane.setStyle("-fx-background-color: transparent;");
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        root.setCenter(scrollPane);

        HBox footer = createFooter();
        root.setBottom(footer);

        Scene scene = new Scene(root, 250, 500);
        window.setScene(scene);

        window.setOnCloseRequest(event -> {
            // just hide, don't destroy
            event.consume();
            hide();
        });
        AppContext.stage.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            window.setAlwaysOnTop(isFocused);
        });

        return window;
    }

    private HBox createHeader() {
        title = new Label(NO_MEASUREMENT);
        title.setStyle("-fx-font-size: 12px; -fx-text-fill: #444444;");

        Color iconColor = Color.valueOf("#666666");
        double iconButtonHeight = 20;

        previousButton = Views.createSvgButton(
                ResourceImageHolder.LEFT,
                iconColor,
                iconButtonHeight,
                "Previous measurement");
        previousButton.setOnAction(e -> selectPreviousTrace());

        nextButton = Views.createSvgButton(
                ResourceImageHolder.RIGHT,
                iconColor,
                iconButtonHeight,
                "Next measurement");
        nextButton.setOnAction(e -> selectNextTrace());

        HBox header = new HBox(5,
                title,
                Views.createSpacer(),
                previousButton,
                nextButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 8, 0));

        return header;
    }

    private HBox createFooter() {
        Button copyAllButton = Views.createFlatButton("Copy All", 18);
        copyAllButton.setTooltip(new Tooltip("Copy all values to clipboard"));
        copyAllButton.setOnAction(e -> copyAllToClipboard());

        HBox footer = new HBox(copyAllButton);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(12, 0, 4, 0));

        return footer;
    }

    private HBox createValueRow(Value value, int rowIndex) {
        TextField nameField = Views.createSelectableLabel(value.header());
        nameField.setMinWidth(120);
        nameField.setMaxWidth(120);

        TextField valueField = Views.createSelectableLabel(value.valueWithUnit());
        valueField.setAlignment(Pos.CENTER_RIGHT);
        valueField.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(valueField, Priority.ALWAYS);

        Button copyButton = Views.createSvgButton(
                ResourceImageHolder.COPY,
                Color.valueOf("#999999"),
                22,
                "Copy value to clipboard");

        copyButton.setOnAction(e -> copyToClipboard(value));

        HBox row = new HBox(2, nameField, valueField, copyButton);
        row.setStyle("-fx-font-size: 12px; -fx-text-fill: #444444;");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(2, 4, 2, 4));
        row.setBackground(Background.fill(Color.valueOf(
                (rowIndex % 2 == 0) ?  "#f3f3f3" : "#fafafa")));

        return row;
    }

    private int getDecimals(String header, Template template) {
        SensorData sensorData = null;
        if (template != null) {
            DataMapping dataMapping = template.getDataMapping();
            if (dataMapping != null) {
                sensorData = dataMapping.getDataValueByHeader(header);
            }
        }
        return sensorData != null
                ? sensorData.getDecimals()
                : SensorData.DEFAULT_DECIMALS;
    }

    private String formatNumber(Number number, int decimals) {
        if (number == null) {
            return NO_VALUE;
        }
        double d = number.doubleValue();
        if (Double.isNaN(d)) {
            return NO_VALUE;
        }
        return String.format("%." + decimals + "f", d);
    }

    public void show() {
        Platform.runLater(() -> {
            if (window == null) {
                window = createWindow();
            }
            updateView();
            window.show();
            window.toFront();
        });
    }

    public void hide() {
        if (window != null) {
            window.hide();
        }
    }

    public void toggle() {
        if (isShowing()) {
            hide();
        } else {
            show();
        }
    }

    public boolean isShowing() {
        return window != null && window.isShowing();
    }

    private @Nullable TraceKey getSelectedTrace(@Nullable SgyFile file) {
        if (file != null) {
            Chart chart = model.getChart(file);
            if (chart != null) {
                return model.getSelectedTrace(chart);
            }
        }
        return null;
    }

    private void updateView() {
        SgyFile file = selectedFile;
        TraceKey trace = getSelectedTrace(file);

        updateWindowTitle(file, trace);
        updateTitle(file, trace);
        updateNavigationButtons(file, trace);
        updateValues(file, trace);
    }

    private void updateWindowTitle(SgyFile file, TraceKey trace) {
        if (window == null) {
            return;
        }
        String windowTitleText = file != null && file.getFile() != null
                ? file.getFile().getName()
                : WINDOW_TITLE;
        window.setTitle(windowTitleText);
    }

    private void updateTitle(SgyFile file, TraceKey trace) {
        if (title == null) {
            return;
        }
        String titleText = trace != null
                ? "Measurement " + (trace.getIndex() + 1) + " of " + file.numTraces()
                : NO_MEASUREMENT;
        title.setText(titleText);
    }

    private void updateNavigationButtons(SgyFile file, TraceKey trace) {
        if (previousButton != null) {
            previousButton.setDisable(trace == null
                    || trace.getIndex() == 0);
        }
        if (nextButton != null) {
            nextButton.setDisable(trace == null
                    || trace.getIndex() == file.numTraces() - 1);
        }
    }

    private List<Value> buildValues(SgyFile file, TraceKey trace) {
        if (file == null || trace == null) {
            return List.of();
        }
        int traceIndex = trace.getIndex();
        GeoData geoData = Nulls.toEmpty(file.getGeoData()).get(traceIndex);
        if (geoData == null) {
            return List.of();
        }
        ColumnSchema schema = geoData.getSchema();
        if (schema == null) {
            return List.of();
        }

        List<Value> values = new ArrayList<>();
        Template template = Templates.getTemplate(file);
        for (Column column : schema) {
            String header = column.getHeader();
            Object value = geoData.getValue(header);

            String valueText;
            String unit = null;
            if (value == null) {
                valueText = NO_VALUE;
            } else if (value instanceof Number number) {
                int decimals = getDecimals(header, template);
                valueText = formatNumber(number, decimals);
                unit = column.getUnit();
            } else {
                valueText = value.toString();
            }
            values.add(new Value(header, valueText, unit));
        }
        return values;
    }

    private void updateValues(SgyFile file, TraceKey trace) {
        this.values = buildValues(file, trace);
        if (valueContainer == null) {
            return;
        }
        valueContainer.getChildren().clear();
        int rowIndex = 0;
        for (Value value : values) {
            HBox row = createValueRow(value, rowIndex++);
            valueContainer.getChildren().add(row);
        }
    }

    private void copyToClipboard(String text) {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);
    }

    private void copyToClipboard(Value value) {
        copyToClipboard(value.value());
    }

    private void copyAllToClipboard() {
        StringBuilder sb = new StringBuilder();
        for (Value value : values) {
            sb.append(value.header()).append(": ").append(value.value());
            sb.append("\n");
        }
        copyToClipboard(sb.toString());
    }

    private void selectPreviousTrace() {
        SgyFile file = selectedFile;
        if (file == null) {
            return;
        }
        TraceKey trace = getSelectedTrace(file);
        if (trace == null) {
            return;
        }
        int traceIndex = trace.getIndex();
        if (traceIndex > 0) {
            TraceKey previousTrace = new TraceKey(file, traceIndex - 1);
            model.selectTrace(previousTrace, true);
        }
    }

    private void selectNextTrace() {
        SgyFile file = selectedFile;
        if (file == null) {
            return;
        }
        TraceKey trace = getSelectedTrace(file);
        if (trace == null) {
            return;
        }
        int traceIndex = trace.getIndex();
        if (traceIndex < file.numTraces() - 1) {
            TraceKey nextTrace = new TraceKey(file, traceIndex + 1);
            model.selectTrace(nextTrace, true);
        }
    }

    @EventListener
    private void onFileSelected(FileSelectedEvent event) {
        SgyFile file = event.getFile();
        if (!Objects.equals(file, selectedFile)) {
            selectedFile = file;
            if (isShowing()) {
                Platform.runLater(this::updateView);
            }
        }
    }

    @EventListener
    private void onFileUpdated(FileUpdatedEvent event) {
        SgyFile file = event.getFile();
        if (Objects.equals(file, selectedFile)) {
            if (isShowing()) {
                Platform.runLater(this::updateView);
            }
        }
    }

    @EventListener
    private void onSeiesUpdated(SeriesUpdatedEvent event) {
        SgyFile file = event.getFile();
        if (Objects.equals(file, selectedFile)) {
            if (isShowing()) {
                Platform.runLater(this::updateView);
            }
        }
    }

    @EventListener
    private void onChange(WhatChanged change) {
        if (change.isTraceSelected()) {
            if (isShowing()) {
                Platform.runLater(this::updateView);
            }
        }
    }

    private record Value (String header, String value, String unit) {

        String valueWithUnit() {
            return !Strings.isNullOrEmpty(unit)
                    ? value + " " + unit
                    : value;
        }
    }
}
