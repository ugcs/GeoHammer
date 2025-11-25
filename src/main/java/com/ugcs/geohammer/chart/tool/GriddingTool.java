package com.ugcs.geohammer.chart.tool;

import com.ugcs.geohammer.PrefSettings;
import com.ugcs.geohammer.chart.csv.SensorLineChart;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.map.layer.GridLayer;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.Range;
import com.ugcs.geohammer.model.TemplateSeriesKey;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
import com.ugcs.geohammer.model.event.WhatChanged;
import com.ugcs.geohammer.service.TaskService;
import com.ugcs.geohammer.service.gridding.GriddingFilter;
import com.ugcs.geohammer.service.gridding.GriddingParams;
import com.ugcs.geohammer.service.gridding.GriddingResult;
import com.ugcs.geohammer.service.gridding.GriddingService;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.util.Templates;
import com.ugcs.geohammer.util.Text;
import com.ugcs.geohammer.view.Views;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.controlsfx.control.RangeSlider;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

@Component
public class GriddingTool extends FilterToolView {

    private static final float SLIDER_EXPAND_THRESHOLD = 0.15f;

    private static final float SLIDER_SHRINK_WIDTH_THRESHOLD = 0.3f;

    private final Model model;

    private final PrefSettings preferences;

    private final GridLayer gridLayer;

    private final GriddingService griddingService;

    private final TaskService taskService;

    // range preferences are not persisted
    private final Map<TemplateSeriesKey, Range> rangePreferences = new HashMap<>();

    // shows that input events from the filter
    // controls should be ignored
    private volatile boolean ignoreFilterEvents;

    // view

    private final Label warning;

    private final TextField cellSizeInput;

    private final TextField blankingDistanceInput;

    private final RangeSlider rangeSlider;

    private final CheckBox hillShading;

    private final CheckBox smoothing;

    private final CheckBox analyticSignal;

    public GriddingTool(
            Model model,
            PrefSettings preferences,
            GridLayer gridLayer,
            GriddingService griddingService,
            TaskService taskService,
            ExecutorService executor
    ) {
        super(executor);

        this.model = model;
        this.preferences = preferences;
        this.gridLayer = gridLayer;
        this.griddingService = griddingService;
        this.taskService = taskService;

        warning = new Label("Warning: Grid needs to be recalculated.");
        warning.setStyle("-fx-text-fill: #E7AE3CFF; -fx-font-weight: bold;");
        warning.setVisible(false);
        warning.setManaged(false);

        cellSizeInput = new TextField();
        cellSizeInput.setPromptText("Enter cell size");
        cellSizeInput.textProperty().addListener(this::onCellSizeChange);

        blankingDistanceInput = new TextField();
        blankingDistanceInput.setPromptText("Enter blanking distance");
        blankingDistanceInput.textProperty().addListener(this::onBlankingDistanceChange);

        // range slider

        HBox labelAndReset = new HBox(10);
        Label label = new Label("Range");
        Button resetButton = Views.createGlyphButton("↺", 16, 16);
        resetButton.setOnAction(e -> initRangeSlider());

        Region labelSeparator = new Region();
        HBox.setHgrow(labelSeparator, Priority.ALWAYS);
        labelAndReset.getChildren().addAll(label, labelSeparator, resetButton);

        Label minLabel = new Label("Min");
        Label maxLabel = new Label("Max");
        Region minMaxSeparator = new Region();
        HBox.setHgrow(minMaxSeparator, Priority.ALWAYS);

        HBox minMaxContainer = new HBox(Tools.DEFAULT_SPACING);
        minMaxContainer.getChildren().addAll(minLabel, minMaxSeparator, maxLabel);

        rangeSlider = createRangeSlider(minLabel, maxLabel);
        VBox rangeContainer = new VBox(10, labelAndReset, rangeSlider, minMaxContainer);

        // post-processing

        hillShading = new CheckBox("Enable hill-shading");
        hillShading.selectedProperty().addListener(this::onHillShadingChange);

        smoothing = new CheckBox("Enable smoothing");
        smoothing.selectedProperty().addListener(this::onSmoothingChange);

        analyticSignal = new CheckBox("Analytic signal");
        analyticSignal.selectedProperty().addListener(this::onAnalyticSignalChange);

        VBox postProcessingContainer = new VBox(Tools.DEFAULT_SPACING,
                hillShading,
                smoothing,
                analyticSignal
        );
        postProcessingContainer.setPadding(new Insets(Tools.DEFAULT_SPACING, 0, Tools.DEFAULT_SPACING, 0));
        postProcessingContainer.setStyle("-fx-border-color: lightgray; -fx-border-width: 1; -fx-border-radius: 5; -fx-padding: 5;");

        inputContainer.getChildren().setAll(
                warning,
                cellSizeInput,
                blankingDistanceInput,
                rangeContainer,
                postProcessingContainer);

        showApply(true);
        showApplyToAll(true);
    }

    @Override
    public boolean isVisibleFor(SgyFile file) {
        return file instanceof CsvFile;
    }

    private RangeSlider createRangeSlider(Label minLabel, Label maxLabel) {
        RangeSlider slider = new RangeSlider();
        slider.setShowTickLabels(true);
        slider.setShowTickMarks(true);
        slider.setLowValue(0);
        slider.setHighValue(Double.MAX_VALUE);
        slider.setShowTickLabels(false);
        slider.setShowTickMarks(false);

        slider.lowValueProperty().addListener((observable, oldValue, newValue) -> {
            setFormattedValue(newValue, "Min: ", minLabel);
            if (!ignoreFilterEvents) {
                saveRangePreferences();
                applyFilter();
            }
        });

        slider.highValueProperty().addListener((observable, oldValue, newValue) -> {
            setFormattedValue(newValue, "Max: ", maxLabel);
            if (!ignoreFilterEvents) {
                saveRangePreferences();
                applyFilter();
            }
        });

        slider.setOnMouseReleased(event -> {
            expandRangeSlider();
        });

        return slider;
    }

    private void setFormattedValue(Number newVal, String prefix, Label label) {
        double range = rangeSlider.getMax() - rangeSlider.getMin();
        String valueText;
        if (range < 10) {
            valueText = String.format(prefix + "%.2f", newVal.doubleValue());
        } else if (range < 100) {
            valueText = String.format(prefix + "%.1f", newVal.doubleValue());
        } else {
            valueText = prefix + newVal.intValue();
        }
        label.setText(valueText);
    }

    private void onCellSizeChange(ObservableValue<? extends String> observable, String oldValue, String newValue) {
        Double cellSize = Text.parseDouble(newValue);
        cellSizeInput.setUserData(cellSize);
        validateInput();

        showWarning(true);
    }

    private void onBlankingDistanceChange(ObservableValue<? extends String> observable, String oldValue, String newValue) {
        Double blankingDistance = Text.parseDouble(newValue);
        blankingDistanceInput.setUserData(blankingDistance);
        validateInput();

        showWarning(true);
    }

    private void onHillShadingChange(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
        if (!ignoreFilterEvents) {
            savePreferences();
            applyFilter();
        }
    }

    private void onSmoothingChange(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
        if (!ignoreFilterEvents) {
            savePreferences();
            applyFilter();
        }
    }

    private void onAnalyticSignalChange(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
        if (!ignoreFilterEvents) {
            savePreferences();
            applyFilter();
        }
    }

    public void validateInput() {
        Double cellSize = (Double) cellSizeInput.getUserData();
        boolean disable = cellSize == null
                || cellSize <= 0
                || cellSize > 100;

        Double blankingDistance = (Double) blankingDistanceInput.getUserData();
        disable = disable
                || blankingDistance == null
                || blankingDistance <= 0
                || blankingDistance > 100;

        disableActions(disable);
    }

    @Override
    public void show(boolean show) {
        super.show(show);

        if (gridLayer != null && gridLayer.isActive() != show) {
            gridLayer.setActive(show);
            gridLayer.submitDraw();
        }
    }

    // warning

    private void showWarning(boolean show) {
        Platform.runLater(() -> {
            warning.setVisible(show);
            warning.setManaged(show);
        });
    }

    // range slider

    private @Nullable Range getSelectedSeriesRange() {
        SgyFile file = selectedFile;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        for (SensorLineChart chart : model.getSensorCharts()) {
            if (Templates.equals(file, chart.getFile())) {
                min = Math.min(min, chart.getSeriesMinValue());
                max = Math.max(max, chart.getSeriesMaxValue());
            }
        }
        Range range = Double.isInfinite(min) || Double.isInfinite(max)
                ? null
                : new Range(min, max);
        if (range != null && range.getWidth() == 0) {
            range = range.scaleToWidth(1, 0.5);
        }
        return range;
    }

    private void initRangeSlider() {
        Range range = getSelectedSeriesRange();
        if (range != null) {
            updateRangeSlider(range);
        }
    }

    private void updateRangeSlider(Range range) {
        rangeSlider.setMax(range.getMax());
        rangeSlider.setMin(range.getMin());

        // assign and adjust values
        if (range.getMax() > rangeSlider.getLowValue()) {
            rangeSlider.adjustHighValue(range.getMax());
            rangeSlider.adjustLowValue(range.getMin());
        } else {
            rangeSlider.adjustLowValue(range.getMin());
            rangeSlider.adjustHighValue(range.getMax());
        }

        expandRangeSlider();
    }

    private void expandRangeSlider() {
        double min = rangeSlider.getMin();
        double max = rangeSlider.getMax();

        double l = rangeSlider.getLowValue();
        double r = rangeSlider.getHighValue();

        // shrink
        if ((r - l) > 1e-12 && (r - l) / (max - min) < SLIDER_SHRINK_WIDTH_THRESHOLD) {
            double center = l + 0.5 * (r - l);
            double centerRatio = (center - min) / (max - min);
            double newWidth = (r - l) / SLIDER_SHRINK_WIDTH_THRESHOLD;

            min = center - centerRatio * newWidth;
            max = center + (1.0 - centerRatio) * newWidth;
        }

        // expand
        double threshold = SLIDER_EXPAND_THRESHOLD * (max - min);
        double k = SLIDER_EXPAND_THRESHOLD; // expand margin ratio to a new width
        if (l - min < threshold) {
            // l - min2 = margin
            // margin = k * (max2 - min2)
            // (r - min2) / (max2 - min2) = (r - min) / (max - min)
            double rRatio = (r - min) / (max - min);
            min = (k * r - rRatio * l) / (k - rRatio);
            max = min + (r - min) / rRatio;
        }
        if (max - r < threshold) {
            // max2 - r = margin
            // margin = k * (max2 - min2)
            // (l - min2) / (max2 - min2) = (l - min) / (max - min)
            double lRatio = (l - min) / (max - min);
            min = (r * lRatio + l * (k - 1)) / (lRatio + k - 1);
            max = (r - k * min) / (1 - k);
        }

        rangeSlider.setMin(min);
        rangeSlider.setMax(max);

        updateRangeSliderTicks();
    }

    private void updateRangeSliderTicks() {
        double width = rangeSlider.getMax() - rangeSlider.getMin();
        if (width > 0.0) {
            rangeSlider.setMajorTickUnit(width / 100);
            rangeSlider.setMinorTickCount((int) (width / 1000));
            rangeSlider.setBlockIncrement(width / 2000);
        }
    }

    @Override
    public void loadPreferences() {
        String templateName = Templates.getTemplateName(selectedFile);
        if (!Strings.isNullOrEmpty(templateName)) {
            Nulls.ifPresent(preferences.getSetting("gridding_cellsize", templateName),
                    cellSizeInput::setText);
            Nulls.ifPresent(preferences.getSetting("gridding_blankingdistance", templateName),
                    blankingDistanceInput::setText);

            ignoreFilterEvents = true;
            try {
                Nulls.ifPresent(preferences.getSetting("gridding_hillshading_enabled", templateName),
                        s -> hillShading.setSelected(Boolean.parseBoolean(s)));
                Nulls.ifPresent(preferences.getSetting("gridding_smoothing_enabled", templateName),
                        s -> smoothing.setSelected(Boolean.parseBoolean(s)));
                Nulls.ifPresent(preferences.getSetting("gridding_analytic_signal_enabled", templateName),
                        s -> analyticSignal.setSelected(Boolean.parseBoolean(s)));
            } finally {
                ignoreFilterEvents = false;
            }
        }

        loadRangePreferences();
    }

    private void loadRangePreferences() {
        TemplateSeriesKey templateSeries = TemplateSeriesKey.ofSeries(
                selectedFile,
                model.getSelectedSeriesName(selectedFile));
        Range range = templateSeries != null
                ? rangePreferences.get(templateSeries)
                : null;
        if (range == null) {
            range = getSelectedSeriesRange();
        }
        if (range != null) {
            ignoreFilterEvents = true;
            try {
                updateRangeSlider(range);
            } finally {
                ignoreFilterEvents = false;
            }
        }
    }

    @Override
    public void savePreferences() {
        String templateName = Templates.getTemplateName(selectedFile);
        if (!Strings.isNullOrEmpty(templateName)) {
            preferences.saveSetting("gridding_cellsize", templateName,
                    cellSizeInput.getText());
            preferences.saveSetting("gridding_blankingdistance", templateName,
                    blankingDistanceInput.getText());

            preferences.saveSetting("gridding_hillshading_enabled", templateName,
                    Boolean.toString(hillShading.isSelected()));
            preferences.saveSetting("gridding_smoothing_enabled", templateName,
                    Boolean.toString(smoothing.isSelected()));
            preferences.saveSetting("gridding_analytic_signal_enabled", templateName,
                    Boolean.toString(analyticSignal.isSelected()));
        }

        saveRangePreferences();
    }

    private void saveRangePreferences() {
        TemplateSeriesKey templateSeries = TemplateSeriesKey.ofSeries(
                selectedFile,
                model.getSelectedSeriesName(selectedFile));
        if (templateSeries != null) {
            Range range = new Range(rangeSlider.getLowValue(), rangeSlider.getHighValue());
            rangePreferences.put(templateSeries, range);
        }
    }

    @Override
    protected void onApply(ActionEvent event) {
        SgyFile file = selectedFile;
        if (file == null) {
            return;
        }
        String seriesName = model.getSelectedSeriesName(file);
        if (Strings.isNullOrEmpty(seriesName)) {
            return;
        }

        applyGridding(List.of(file), seriesName);
    }

    @Override
    protected void onApplyToAll(ActionEvent event) {
        SgyFile file = selectedFile;
        if (file == null) {
            return;
        }
        String seriesName = model.getSelectedSeriesName(file);
        if (Strings.isNullOrEmpty(seriesName)) {
            return;
        }
        List<SgyFile> files = model.getFileManager().getFiles().stream()
                .filter(f -> Templates.equals(f, file))
                .toList();

        applyGridding(files, seriesName);
    }

    private GriddingParams getParams() {
        Double cellSize = (Double)cellSizeInput.getUserData();
        Double blankingDistance = (Double)blankingDistanceInput.getUserData();
        if (cellSize == null || blankingDistance == null) {
            return null;
        }
        return new GriddingParams(
                cellSize,
                blankingDistance
        );
    }

    private GriddingFilter getFilter() {
        return new GriddingFilter(
                new Range(rangeSlider.getLowValue(), rangeSlider.getHighValue()),
                analyticSignal.isSelected(),
                hillShading.isSelected(),
                smoothing.isSelected()
        );
    }

    private void publishFilter() {
        SgyFile file = selectedFile;
        String seriesName = model.getSelectedSeriesName(file);

        GriddingFilter filter = getFilter();
        gridLayer.setFilter(file, seriesName, filter);
    }

    private void applyFilter() {
        publishFilter();
        gridLayer.submitDraw();
    }

    private void applyGridding(Collection<SgyFile> files, String seriesName) {
        GriddingParams params = getParams();
        if (params == null) {
            return;
        }

        var future = submitAction(() -> {
            publishFilter();
            GriddingResult result = griddingService.runGridding(files, seriesName, params);
            for (SgyFile targetFile : files) {
                gridLayer.setResult(targetFile, result);
            }
            gridLayer.submitDraw();
            showWarning(false);
            return result;
        });

        String taskName = "Gridding " + seriesName;
        taskService.registerTask(future, taskName);
    }

    @EventListener
    protected void onFileSelected(FileSelectedEvent event) {
        Platform.runLater(() -> selectFile(event.getFile(), false));
    }

    @EventListener
    public void onChange(WhatChanged changed) {
        if (changed.isTraceCut()) {
            showWarning(true);
        }
        if (changed.isCsvDataFiltered()) {
            showWarning(true);
            // TODO
            //applyGridding();
        }
    }
}
