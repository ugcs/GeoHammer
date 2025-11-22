package com.ugcs.geohammer.chart.tool;

import com.ugcs.geohammer.PrefSettings;
import com.ugcs.geohammer.chart.csv.SensorLineChart;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.Range;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
import com.ugcs.geohammer.model.event.GriddingParamsSetted;
import com.ugcs.geohammer.model.event.WhatChanged;
import com.ugcs.geohammer.model.template.Template;
import com.ugcs.geohammer.util.Check;
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
import org.jspecify.annotations.NonNull;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

@Component
public class GriddingView extends FilterToolView {

    private static final float SLIDER_EXPAND_THRESHOLD = 0.15f;

    private static final float SLIDER_SHRINK_WIDTH_THRESHOLD = 0.3f;

    private final Model model;

    private final PrefSettings preferences;

    // view

    // Warning label for grid input data changes
    private final Label griddingWarningLabel;

    private final TextField cellSizeInput;

    private final TextField blankingDistanceInput;

    private final RangeSlider griddingRangeSlider;

    private final Map<TemplateSeriesKey, GriddingRange> griddingRanges = new HashMap<>();

    private final CheckBox hillShading;

    private final CheckBox smoothing;

    private final CheckBox analyticSignal;

    public GriddingView(Model model, PrefSettings preferences, ExecutorService executor) {
        super(executor);

        this.model = model;
        this.preferences = preferences;

        griddingWarningLabel = new Label("Warning: Grid needs to be recalculated.");
        griddingWarningLabel.setStyle("-fx-text-fill: #E7AE3CFF; -fx-font-weight: bold;");
        griddingWarningLabel.setVisible(false);
        griddingWarningLabel.setManaged(false);

        cellSizeInput = new TextField();
        cellSizeInput.setPromptText("Enter cell size");
        cellSizeInput.textProperty().addListener(this::onCellSizeChange);
        //filterInputs.put(Filter.gridding_cellsize.name(), cellSizeInput);

        blankingDistanceInput = new TextField();
        blankingDistanceInput.setPromptText("Enter blanking distance");
        blankingDistanceInput.textProperty().addListener(this::onBlankingDistanceChange);
        //filterInputs.put(Filter.gridding_blankingdistance.name(), blankingDistanceInput);

        // range slider

        HBox labelAndReset = new HBox(10);
        Label label = new Label("Range");
        Button resetButton = Views.createGlyphButton("↺", 16, 16);
        resetButton.setOnAction(this::onResetGriddingRange);

        Region labelSeparator = new Region();
        HBox.setHgrow(labelSeparator, Priority.ALWAYS);
        labelAndReset.getChildren().addAll(label, labelSeparator, resetButton);

        Label minLabel = new Label("Min");
        Label maxLabel = new Label("Max");
        Region minMaxSeparator = new Region();
        HBox.setHgrow(minMaxSeparator, Priority.ALWAYS);

        HBox minMaxContainer = new HBox(Tools.DEFAULT_SPACING);
        minMaxContainer.getChildren().addAll(minLabel, minMaxSeparator, maxLabel);

        griddingRangeSlider = createRangeSlider(minLabel, maxLabel);
        VBox rangeContainer = new VBox(10, labelAndReset, griddingRangeSlider, minMaxContainer);

        // post-processing

        //filterInputs.put(Filter.gridding_hillshading_enabled.name(), hillShadingEnabledText);
        //filterInputs.put(Filter.gridding_smoothing_enabled.name(), smoothingEnabledText);
        //filterInputs.put(Filter.gridding_analytic_signal_enabled.name(), analyticSignalEnabledText);

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
                griddingWarningLabel,
                cellSizeInput,
                blankingDistanceInput,
                rangeContainer,
                postProcessingContainer);

        showApply(true);
        showApplyToAll(true);
    }

    private RangeSlider createRangeSlider(Label minLabel, Label maxLabel) {
        RangeSlider slider = new RangeSlider();
        slider.setShowTickLabels(true);
        slider.setShowTickMarks(true);
        slider.setLowValue(0);
        slider.setHighValue(Double.MAX_VALUE);
        slider.setDisable(true);
        slider.setShowTickLabels(false);
        slider.setShowTickMarks(false);

        slider.lowValueProperty().addListener(
                (observable, oldValue, newValue) -> {
            setFormattedValue(newValue, "Min: ", minLabel);
            saveGriddingRange();
            model.publishEvent(new WhatChanged(this, WhatChanged.Change.griddingRange));
        });

        slider.highValueProperty().addListener(
                (observable, oldValue, newValue) -> {
            setFormattedValue(newValue, "Max: ", maxLabel);
            saveGriddingRange();
            model.publishEvent(new WhatChanged(this, WhatChanged.Change.griddingRange));
        });

        slider.setOnMouseReleased(event -> {
            expandGriddingRangeSlider();
            saveGriddingRange();
        });

        return slider;
    }

    private void setFormattedValue(Number newVal, String prefix, Label label) {
        double range = griddingRangeSlider.getMax() - griddingRangeSlider.getMin();
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

         showGridInputDataChangedWarning(true);
    }

    private void onBlankingDistanceChange(ObservableValue<? extends String> observable, String oldValue, String newValue) {
        Double blankingDistance = Text.parseDouble(newValue);
        blankingDistanceInput.setUserData(blankingDistance);
        validateInput();

        showGridInputDataChangedWarning(true);
    }

    private void onHillShadingChange(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
        savePreferences();
        publishGriddingParameters(this, false);
    }

    private void onSmoothingChange(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
        saveGriddingRange();
        publishGriddingParameters(this, false);
    }

    private void onAnalyticSignalChange(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
        savePreferences();
        publishGriddingParameters(this, false);
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

    // warning

    private void showGridInputDataChangedWarning(boolean show) {
        if (griddingWarningLabel != null) {
            Platform.runLater(() -> {
                griddingWarningLabel.setVisible(show);
                griddingWarningLabel.setManaged(show);
            });
        }
    }

    // range slider

    private void onResetGriddingRange(ActionEvent event) {
        // get current min/max range
        Range range = getSelectedSeriesRange();
        if (range == null) {
            return;
        }
        if (range.getWidth() == 0) {
            range = range.scaleToWidth(1, 0.5);
        }
        GriddingRange griddingRange = new GriddingRange(
                range.getMin().doubleValue(),
                range.getMax().doubleValue(),
                range.getMin().doubleValue(),
                range.getMax().doubleValue());
        updateGriddingRangeSlider(griddingRange);
    }

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
        return Double.isInfinite(min) || Double.isInfinite(max)
                ? null
                : new Range(min, max);
    }

    private void updateGriddingRangeSlider(GriddingRange sliderRange) {
        griddingRangeSlider.setMax(sliderRange.max());
        griddingRangeSlider.setMin(sliderRange.min());

        // assign and adjust values
        if (sliderRange.highValue() > griddingRangeSlider.getLowValue()) {
            griddingRangeSlider.adjustHighValue(sliderRange.highValue());
            griddingRangeSlider.adjustLowValue(sliderRange.lowValue());
        } else {
            griddingRangeSlider.adjustLowValue(sliderRange.lowValue());
            griddingRangeSlider.adjustHighValue(sliderRange.highValue());
        }

        expandGriddingRangeSlider();
        saveGriddingRange();
    }

    private void expandGriddingRangeSlider() {
        double min = griddingRangeSlider.getMin();
        double max = griddingRangeSlider.getMax();

        double l = griddingRangeSlider.getLowValue();
        double r = griddingRangeSlider.getHighValue();

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

        griddingRangeSlider.setMin(min);
        griddingRangeSlider.setMax(max);

        updateGriddingRangeSliderTicks();
    }

    private void updateGriddingRangeSliderTicks() {
        double width = griddingRangeSlider.getMax() - griddingRangeSlider.getMin();
        if (width > 0.0) {
            griddingRangeSlider.setMajorTickUnit(width / 100);
            griddingRangeSlider.setMinorTickCount((int) (width / 1000));
            griddingRangeSlider.setBlockIncrement(width / 2000);
        }
    }

    // gridding ranges

    public GriddingRange getGriddingRange(CsvFile csvFile, String seriesName) {
        Check.notNull(csvFile);
        Check.notEmpty(seriesName);

        GriddingRange range = null;
        Template template = csvFile.getTemplate();
        if (template != null) {
            TemplateSeriesKey rangeKey = new TemplateSeriesKey(template.getName(), seriesName);
            range = griddingRanges.get(rangeKey);
        }
        return range != null
                ? range
                : getGriddingRangeSliderValues();
    }

    private @NonNull GriddingRange getGriddingRangeSliderValues() {
        return new GriddingRange(griddingRangeSlider.getLowValue(),
                griddingRangeSlider.getHighValue(),
                griddingRangeSlider.getMin(),
                griddingRangeSlider.getMax());
    }

    private void saveGriddingRange() {
        if (!(selectedFile instanceof CsvFile csvFile)) {
            return;
        }
        Template template = csvFile.getTemplate();
        if (template == null) {
            return;
        }
        SensorLineChart chart = model.getCsvChart(csvFile);
        if (chart == null) {
            return;
        }
        TemplateSeriesKey rangeKey = new TemplateSeriesKey(
                template.getName(),
                chart.getSelectedSeriesName());
        GriddingRange griddingRange = getGriddingRangeSliderValues();
        griddingRanges.put(rangeKey, griddingRange);
    }

    private Optional<GriddingRange> loadGriddingRange() {
        if (!(selectedFile instanceof CsvFile csvFile)) {
            return Optional.empty();
        }
        Template template = csvFile.getTemplate();
        if (template == null) {
            return Optional.empty();
        }
        SensorLineChart chart = model.getCsvChart(csvFile);
        if (chart == null) {
            return Optional.empty();
        }

        TemplateSeriesKey rangeKey = new TemplateSeriesKey(
                template.getName(),
                chart.getSelectedSeriesName());
        return Optional.ofNullable(griddingRanges.get(rangeKey));
    }

    private void updateGriddingMinMaxPreserveUserRange() {
        updateGriddingMinMaxPreserveUserRange(null);
    }

    /**
     * Updates the gridding range slider min/max values if needed, while maintaining
     * the user's selected range proportionally.
     */
    private void updateGriddingMinMaxPreserveUserRange(Range rangeBefore) {
        if (!(selectedFile instanceof CsvFile)) {
            return;
        }

        // get current min/max range
        Range range = getSelectedSeriesRange();
        if (range == null) {
            return;
        }
        if (range.getWidth() == 0) {
            range = range.scaleToWidth(1, 0.5);
        }

        GriddingRange defaultGriddingRange = new GriddingRange(
                range.getMin().doubleValue(),
                range.getMax().doubleValue(),
                range.getMin().doubleValue(),
                range.getMax().doubleValue());
        GriddingRange griddingRange = loadGriddingRange().orElse(defaultGriddingRange);

        // Only update min/max if they changed
        if (rangeBefore != null
                && (!Objects.equals(rangeBefore.getMin(), range.getMin())
                || !Objects.equals(rangeBefore.getMax(), range.getMax()))) {

            // Calculate relative positions within the range
            double widthBefore = rangeBefore.getWidth();
            double lowRatio = widthBefore > 0
                    ? (griddingRange.lowValue() - rangeBefore.getMin().doubleValue()) / widthBefore
                    : 0;
            lowRatio = Math.clamp(lowRatio, 0, 1);
            double highRatio = widthBefore > 0
                    ? (griddingRange.highValue() - rangeBefore.getMin().doubleValue()) / widthBefore
                    : 1;
            highRatio = Math.clamp(highRatio, 0, 1);

            // maintain the relative range of user's selection
            griddingRange = new GriddingRange(
                    range.getMin().doubleValue() + lowRatio * range.getWidth(),
                    range.getMin().doubleValue() + highRatio * range.getWidth(),
                    range.getMin().doubleValue(),
                    range.getMax().doubleValue());
        }

        updateGriddingRangeSlider(griddingRange);
        griddingRangeSlider.setDisable(false);
    }

    @Override
    public void loadPreferences() {
        String templateName = Templates.getTemplateName(selectedFile);
        if (!Strings.isNullOrEmpty(templateName)) {
            Nulls.ifPresent(preferences.getSetting("gridding_cellsize", templateName),
                    cellSizeInput::setText);
            Nulls.ifPresent(preferences.getSetting("gridding_blankingdistance", templateName),
                    blankingDistanceInput::setText);
            Nulls.ifPresent(preferences.getSetting("gridding_hillshading_enabled", templateName),
                    s -> hillShading.setSelected(Boolean.parseBoolean(s)));
            Nulls.ifPresent(preferences.getSetting("gridding_smoothing_enabled", templateName),
                    s -> smoothing.setSelected(Boolean.parseBoolean(s)));
            Nulls.ifPresent(preferences.getSetting("gridding_analytic_signal_enabled", templateName),
                    s -> analyticSignal.setSelected(Boolean.parseBoolean(s)));
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
    }

    @Override
    public void apply() {
        publishGriddingParameters(applyButton, false);
        showGridInputDataChangedWarning(false);
    }

    @Override
    public void applyToAll() {
        publishGriddingParameters(applyToAllButton, true);
        showGridInputDataChangedWarning(false);
    }

    private void publishGriddingParameters(Object source, boolean toAll) {
        Double cellSize = (Double)cellSizeInput.getUserData();
        Double blankingDistance = (Double)blankingDistanceInput.getUserData();

        model.publishEvent(new GriddingParamsSetted(
                source,
                cellSize,
                blankingDistance,
                toAll,
                analyticSignal.isSelected(),
                hillShading.isSelected(),
                smoothing.isSelected()
        ));
    }

    @Override
    @EventListener
    protected void onFileSelected(FileSelectedEvent event) {
        super.onFileSelected(event);

        // TODO: check if possible, that current file and sensor
        //  was not gridded with current parameters
        showGridInputDataChangedWarning(true);
        Platform.runLater(this::updateGriddingMinMaxPreserveUserRange);
    }

    @EventListener
    public void onChange(WhatChanged changed) {
        if (changed.isTraceCut()) {
            showGridInputDataChangedWarning(true);
        }
    }
}
