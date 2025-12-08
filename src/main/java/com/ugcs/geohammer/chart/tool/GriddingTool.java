package com.ugcs.geohammer.chart.tool;

import com.ugcs.geohammer.PrefSettings;
import com.ugcs.geohammer.chart.csv.SensorLineChart;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.map.layer.GridLayer;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.Range;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
import com.ugcs.geohammer.model.event.SeriesSelectedEvent;
import com.ugcs.geohammer.model.event.SeriesUpdatedEvent;
import com.ugcs.geohammer.model.event.WhatChanged;
import com.ugcs.geohammer.service.TaskService;
import com.ugcs.geohammer.service.gridding.GriddingFilter;
import com.ugcs.geohammer.service.gridding.GriddingParams;
import com.ugcs.geohammer.service.gridding.GriddingResult;
import com.ugcs.geohammer.service.gridding.GriddingService;
import com.ugcs.geohammer.util.Formats;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.util.Templates;
import com.ugcs.geohammer.util.Text;
import com.ugcs.geohammer.view.Views;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.controlsfx.control.RangeSlider;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class GriddingTool extends FilterToolView {

    private static final float SLIDER_EXPAND_THRESHOLD = 0.15f;

    private static final float SLIDER_SHRINK_WIDTH_THRESHOLD = 0.3f;

    private final Model model;

    private final PrefSettings preferences;

    private final GridLayer gridLayer;

    private final GriddingService griddingService;

    private final TaskService taskService;

    // shows that input events from the filter
    // controls should be ignored;
    // alternative to this flag is disabling listeners
    // during the preference loading stage which makes
    // code messier
    private final AtomicBoolean ignoreFilterEvents = new AtomicBoolean(false);

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

        Label minLabel = new Label("min");
        minLabel.setStyle("-fx-text-fill: #888888;");

        TextField minInput = new TextField();
        minInput.setMinWidth(50);
        minInput.setPrefWidth(80);

        Label maxLabel = new Label("max");
        maxLabel.setStyle("-fx-text-fill: #888888;");

        TextField maxInput = new TextField();
        maxInput.setAlignment(Pos.BASELINE_RIGHT);
        maxInput.setMinWidth(50);
        maxInput.setPrefWidth(80);

        Region minMaxSeparator = new Region();
        HBox.setHgrow(minMaxSeparator, Priority.ALWAYS);

        HBox minMaxContainer = new HBox(Tools.DEFAULT_SPACING);
        minMaxContainer.setAlignment(Pos.BASELINE_CENTER);
        minMaxContainer.getChildren().addAll(minLabel, minInput, minMaxSeparator, maxInput, maxLabel);

        rangeSlider = createRangeSlider(minInput, maxInput);
        VBox rangeContainer = new VBox(10, labelAndReset, rangeSlider, minMaxContainer);

        // post-processing

        hillShading = new CheckBox("Enable hill-shading");
        hillShading.selectedProperty().addListener(this::onFilterOptionChange);

        smoothing = new CheckBox("Enable smoothing");
        smoothing.selectedProperty().addListener(this::onFilterOptionChange);

        analyticSignal = new CheckBox("Analytic signal");
        analyticSignal.selectedProperty().addListener(this::onFilterOptionChange);

        VBox postProcessingContainer = new VBox(Tools.DEFAULT_SPACING,
                hillShading,
                smoothing,
                analyticSignal
        );
        postProcessingContainer.setStyle("-fx-border-color: lightgray; -fx-border-width: 1; -fx-border-radius: 5; -fx-padding: 8;");
        VBox.setMargin(postProcessingContainer,  new Insets(Tools.DEFAULT_SPACING, 0, Tools.DEFAULT_SPACING, 0));

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

    private RangeSlider createRangeSlider(TextField minInput, TextField maxInput) {
        RangeSlider slider = new RangeSlider();
        slider.setShowTickLabels(true);
        slider.setShowTickMarks(true);
        slider.setLowValue(0);
        slider.setHighValue(Double.MAX_VALUE);
        slider.setShowTickLabels(false);
        slider.setShowTickMarks(false);

        slider.lowValueProperty().addListener((observable, oldValue, newValue) -> {
            minInput.setText(Formats.prettyForRange(newValue, rangeSlider.getMin(), rangeSlider.getMax()));
            if (!ignoreFilterEvents.get()) {
                applyFilter();
            }
        });

        slider.highValueProperty().addListener((observable, oldValue, newValue) -> {
            maxInput.setText(Formats.prettyForRange(newValue, rangeSlider.getMin(), rangeSlider.getMax()));
            if (!ignoreFilterEvents.get()) {
                applyFilter();
            }
        });

        slider.setOnMouseReleased(event -> {
            expandRangeSlider();
        });

        minInput.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                Double low = Text.parseDouble(minInput.getText());
                if (low != null) {
                    setRangeSliderLow(low);
                }
                event.consume();
            }
            if (event.getCode() == KeyCode.ESCAPE) {
                minInput.setText(Formats.prettyForRange(
                        rangeSlider.getLowValue(), rangeSlider.getMin(), rangeSlider.getMax()));
                event.consume();
            }
        });

        maxInput.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                Double high = Text.parseDouble(maxInput.getText());
                if (high != null) {
                    setRangeSliderHigh(high);
                }
                event.consume();
            }
            if (event.getCode() == KeyCode.ESCAPE) {
                minInput.setText(Formats.prettyForRange(
                        rangeSlider.getHighValue(), rangeSlider.getMin(), rangeSlider.getMax()));
                event.consume();
            }
        });

        return slider;
    }

    private void onCellSizeChange(ObservableValue<? extends String> observable, String oldValue, String newValue) {
        Double cellSize = Text.parseDouble(newValue);
        cellSizeInput.setUserData(cellSize);
        onInputChange();
    }

    private void onBlankingDistanceChange(ObservableValue<? extends String> observable, String oldValue, String newValue) {
        Double blankingDistance = Text.parseDouble(newValue);
        blankingDistanceInput.setUserData(blankingDistance);
        onInputChange();
    }

    private void onFilterOptionChange(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
        if (!ignoreFilterEvents.get()) {
            applyFilter();
        }
    }

    private void onInputChange() {
        // validate input
        GriddingParams params = getParams();
        boolean disable = params == null
                || params.cellSize() <= 0
                || params.cellSize() > 100
                || params.blankingDistance() <= 0
                || params.blankingDistance() > 100;
        disableActions(disable);

        // show/hide params change warning
        boolean paramsChanged = checkParamsChanged();
        showParamsChangedWarning(paramsChanged);
    }

    private boolean checkParamsChanged() {
        SgyFile file = selectedFile;
        if (file == null) {
            return false;
        }
        GriddingResult griddingResult = gridLayer.getResult(file);
        if (griddingResult == null) {
            return false;
        }
        String seriesName = model.getSelectedSeriesName(file);
        if (!Objects.equals(seriesName, griddingResult.seriesName())) {
            return true;
        }
        return !Objects.equals(getParams(), griddingResult.params());
    }

    private void showParamsChangedWarning(boolean show) {
        Platform.runLater(() -> {
            warning.setVisible(show);
            warning.setManaged(show);
        });
    }

    @Override
    public void show(boolean show) {
        super.show(show);

        if (gridLayer != null && gridLayer.isActive() != show) {
            gridLayer.setActive(show);
            gridLayer.submitDraw();
        }
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

    private void setRangeSliderLow(double low) {
        if (low < rangeSlider.getMin()) {
            rangeSlider.setMin(low);
        } else if (low > rangeSlider.getHighValue()) {
            if (low > rangeSlider.getMax()) {
                rangeSlider.setMax(low);
            }
            rangeSlider.setHighValue(low);
        }
        rangeSlider.setLowValue(low);
        expandRangeSlider();
    }

    private void setRangeSliderHigh(double high) {
        if (high > rangeSlider.getMax()) {
            rangeSlider.setMax(high);
        } else if (high < rangeSlider.getLowValue()) {
            if (high < rangeSlider.getMin()) {
                rangeSlider.setMin(high);
            }
            rangeSlider.setLowValue(high);
        }
        rangeSlider.setHighValue(high);
        expandRangeSlider();
    }

    private void updateRangeSlider(Range range) {
        rangeSlider.setMin(range.getMin());
        rangeSlider.setMax(range.getMax());

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
        ignoreFilterEvents.set(true);
        try {
            String templateName = Templates.getTemplateName(selectedFile);
            if (!Strings.isNullOrEmpty(templateName)) {
                cellSizeInput.setText(preferences.getStringOrDefault(
                        "gridding_cellsize", templateName, Strings.empty()));
                blankingDistanceInput.setText(preferences.getStringOrDefault(
                        "gridding_blankingdistance", templateName, Strings.empty()));
                hillShading.setSelected(preferences.getBooleanOrDefault(
                        "gridding_hillshading_enabled", templateName, false));
                smoothing.setSelected(preferences.getBooleanOrDefault(
                        "gridding_smoothing_enabled", templateName, false));
                analyticSignal.setSelected(preferences.getBooleanOrDefault(
                        "gridding_analytic_signal_enabled", templateName, false));
            }

            loadRangePreferences();
        } finally {
            ignoreFilterEvents.set(false);
        }
    }

    private void loadRangePreferences() {
        String templateName = Templates.getTemplateName(selectedFile);
        String seriesName = model.getSelectedSeriesName(selectedFile);

        Range range = null;
        if (!Strings.isNullOrEmpty(templateName) && !Strings.isNullOrEmpty(seriesName)) {
            // range
            Double rangeMin = preferences.getDouble(
                    "gridding_range_min", templateName + "." + seriesName);
            Double rangeMax = preferences.getDouble(
                    "gridding_range_max", templateName + "." + seriesName);
            if (rangeMin != null && rangeMax != null) {
                range = new Range(rangeMin, rangeMax);
            }
        }
        // use default series range if no persisted values found
        if (range == null) {
            range = getSelectedSeriesRange();
        }
        // apply range
        if (range != null) {
            updateRangeSlider(range);
        }
    }

    @Override
    public void savePreferences() {
        String templateName = Templates.getTemplateName(selectedFile);
        if (!Strings.isNullOrEmpty(templateName)) {
            preferences.setValue("gridding_cellsize", templateName,
                    cellSizeInput.getText());
            preferences.setValue("gridding_blankingdistance", templateName,
                    blankingDistanceInput.getText());
            preferences.setValue("gridding_hillshading_enabled", templateName,
                    Boolean.toString(hillShading.isSelected()));
            preferences.setValue("gridding_smoothing_enabled", templateName,
                    Boolean.toString(smoothing.isSelected()));
            preferences.setValue("gridding_analytic_signal_enabled", templateName,
                    Boolean.toString(analyticSignal.isSelected()));
        }

        saveRangePreferences();
    }

    private void saveRangePreferences() {
        String templateName = Templates.getTemplateName(selectedFile);
        String seriesName = model.getSelectedSeriesName(selectedFile);

        if (!Strings.isNullOrEmpty(templateName) && !Strings.isNullOrEmpty(seriesName)) {
            preferences.setValue("gridding_range_min", templateName + "." + seriesName,
                    Text.formatNumber(rangeSlider.getLowValue()));
            preferences.setValue("gridding_range_max", templateName + "." + seriesName,
                    Text.formatNumber(rangeSlider.getHighValue()));
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
        savePreferences();
        publishFilter();
        gridLayer.submitDraw();
    }

    private void applyGridding(Collection<SgyFile> files, String seriesName) {
        GriddingParams params = getParams();
        if (params == null) {
            return;
        }

        var future = submitAction(() -> {
            showParamsChangedWarning(false);
            publishFilter();
            GriddingResult result = griddingService.runGridding(files, seriesName, params);
            for (SgyFile targetFile : files) {
                gridLayer.setResult(targetFile, result);
            }
            gridLayer.submitDraw();
            return result;
        });

        String taskName = "Gridding " + seriesName;
        taskService.registerTask(future, taskName);
    }

    @EventListener
    protected void onFileSelected(FileSelectedEvent event) {
        Platform.runLater(() -> selectFile(event.getFile()));
    }

    @EventListener
    private void onSeriesSelected(SeriesSelectedEvent event) {
        if (Objects.equals(selectedFile, event.getFile())) {
            Platform.runLater(() -> {
                loadRangePreferences();
                onInputChange();
            });
        }
    }

    @EventListener
    private void onChange(WhatChanged changed) {
        if (changed.isTraceCut()) {
            showParamsChangedWarning(true);
        }
    }

    @EventListener
    private void onSeriesUpdated(SeriesUpdatedEvent event) {
        SgyFile file = selectedFile;
        if (!Objects.equals(file, event.getFile())) {
            return;
        }
        GriddingResult griddingResult = gridLayer.getResult(file);
        if (griddingResult == null) {
            return;
        }
        String seriesName = Nulls.toEmpty(griddingResult.seriesName());
        if (!Objects.equals(seriesName, event.getSeriesName())) {
            return;
        }

        // check if gridding should be submitted automatically
        boolean resubmit = gridLayer.isActive() && seriesName.endsWith("_LAG");
        if (resubmit) {
            applyGridding(List.of(file), seriesName);
        } else {
            showParamsChangedWarning(true);
        }
    }
}