package com.ugcs.geohammer.chart.tool;

import com.ugcs.geohammer.chart.csv.SensorLineChart;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.format.nmea.NmeaFile;
import com.ugcs.geohammer.format.svlog.SonarFile;
import com.ugcs.geohammer.map.layer.GridLayer;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.Range;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
import com.ugcs.geohammer.model.event.GridUpdatedEvent;
import com.ugcs.geohammer.model.event.SeriesSelectedEvent;
import com.ugcs.geohammer.model.event.SeriesUpdatedEvent;
import com.ugcs.geohammer.model.event.WhatChanged;
import com.ugcs.geohammer.service.TaskService;
import com.ugcs.geohammer.service.gridding.GriddingFilter;
import com.ugcs.geohammer.service.gridding.GriddingParams;
import com.ugcs.geohammer.service.gridding.GriddingSettings;
import com.ugcs.geohammer.service.gridding.GriddingResult;
import com.ugcs.geohammer.service.gridding.GriddingService;
import com.ugcs.geohammer.service.magnetics.RtpDirection;
import com.ugcs.geohammer.service.magnetics.RtpDirectionService;
import com.ugcs.geohammer.service.palette.PaletteType;
import com.ugcs.geohammer.service.palette.SpectrumType;
import com.ugcs.geohammer.util.Formats;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.util.Templates;
import com.ugcs.geohammer.util.Text;
import com.ugcs.geohammer.view.ResourceImageHolder;
import com.ugcs.geohammer.view.Dialogs;
import com.ugcs.geohammer.view.Views;
import com.ugcs.geohammer.view.control.InputWithTopLabel;
import com.ugcs.geohammer.view.control.NodeWithTopLabel;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.controlsfx.control.RangeSlider;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class GriddingTool extends FilterToolView {

    private static final float SLIDER_EXPAND_THRESHOLD = 0.15f;

    private static final float SLIDER_SHRINK_WIDTH_THRESHOLD = 0.3f;

    private final Model model;

    private final GridLayer gridLayer;

    private final GriddingService griddingService;

    private final TaskService taskService;

    private final PaletteView paletteView;

    private final GriddingSettings griddingSettings;

    private final RtpDirectionService rtpDirectionService;

    // shows that input events from the filter controls should be ignored;
    // alternative to this flag is disabling listeners during the preference
    // loading stage which makes code messier
    private final AtomicBoolean ignoreFilterEvents = new AtomicBoolean(false);

    // view

    private final Label warning;

    private final ComboBox<String> inputSeriesSelector;

    private final ComboBox<String> displayedGridSelector;

    private final TextField cellSizeInput;

    private final TextField blankingDistanceInput;

    private final RangeSlider rangeSlider;

    private final CheckBox hillShading;

    private final CheckBox smoothing;

    private final CheckBox analyticSignal;

    private final CheckBox reductionToPole;

    private final TextField rtpInclinationInput;

    private final TextField rtpDeclinationInput;

    private final TextField rtpFallbackDateInput;

    private final ComboBox<PaletteType> paletteSelector;

    private final ComboBox<SpectrumType> spectrumSelector;

    private boolean updatingGridSelectors;

    public GriddingTool(
            Model model,
            GridLayer gridLayer,
            PaletteView paletteView,
            GriddingService griddingService,
            GriddingSettings griddingSettings,
            RtpDirectionService rtpDirectionService,
            TaskService taskService,
            ExecutorService executor
    ) {
        super(executor);

        this.model = model;
        this.gridLayer = gridLayer;
        this.paletteView = paletteView;
        this.griddingService = griddingService;
        this.griddingSettings = griddingSettings;
        this.rtpDirectionService = rtpDirectionService;
        this.taskService = taskService;

        warning = new Label("Warning: Grid needs to be recalculated.");
        warning.getStyleClass().add("warning");
        warning.setVisible(false);
        warning.setManaged(false);

        inputSeriesSelector = new ComboBox<>();
        inputSeriesSelector.setMaxWidth(Double.MAX_VALUE);
        inputSeriesSelector.setOnAction(event -> onInputSeriesSelected());

        displayedGridSelector = new ComboBox<>();
        displayedGridSelector.setMaxWidth(Double.MAX_VALUE);
        displayedGridSelector.setOnAction(event -> onDisplayedGridSelected());

        VBox gridSelectionGroup = createGroup(
                new NodeWithTopLabel<>("Input series", inputSeriesSelector),
                new NodeWithTopLabel<>("Displayed saved grid", displayedGridSelector));

        InputWithTopLabel cellSizeWithLabel = new InputWithTopLabel("Cell size (m)");
        cellSizeInput = cellSizeWithLabel.getInput();
        cellSizeInput.textProperty().addListener(this::onCellSizeChange);

        InputWithTopLabel blankingDistanceWithLabel = new InputWithTopLabel("Blanking distance (m)");
        blankingDistanceInput = blankingDistanceWithLabel.getInput();
        blankingDistanceInput.textProperty().addListener(this::onBlankingDistanceChange);

        HBox inputGroup = new HBox(Views.DEFAULT_SPACING,
                cellSizeWithLabel,
                blankingDistanceWithLabel);

        // palette

        paletteSelector = createPaletteSelector();
        HBox.setHgrow(paletteSelector, Priority.ALWAYS);
        paletteSelector.setPrefWidth(0); // allow equal distribution
        spectrumSelector = createSpectrumSelector();
        HBox.setHgrow(spectrumSelector, Priority.ALWAYS);
        spectrumSelector.setPrefWidth(0); // allow equal distribution

        Button showPalette = Views.createSvgButton(
                ResourceImageHolder.HISTOGRAM, 24, "Inspect palette");
        showPalette.setOnAction(e -> {
            paletteView.toggle();
        });

        HBox paletteGroup = new HBox(Views.DEFAULT_SPACING,
                paletteSelector,
                spectrumSelector,
                showPalette);
        paletteGroup.setAlignment(Pos.BASELINE_CENTER);
        VBox.setMargin(paletteGroup,  new Insets(2, 0, 0, 0));

        // range slider

        HBox labelAndReset = new HBox(10);
        Label label = new Label("Range");
        Button resetButton = Views.createGlyphButton("↺", 16, 16);
        resetButton.setOnAction(e -> initRangeSlider());

        Region labelSeparator = new Region();
        HBox.setHgrow(labelSeparator, Priority.ALWAYS);
        labelAndReset.getChildren().addAll(label, labelSeparator, resetButton);

        Label minLabel = new Label("min");
        minLabel.getStyleClass().add("dim");

        TextField minInput = new TextField();
        minInput.setMinWidth(50);
        minInput.setPrefWidth(80);

        Label maxLabel = new Label("max");
        maxLabel.getStyleClass().add("dim");

        TextField maxInput = new TextField();
        maxInput.setAlignment(Pos.BASELINE_RIGHT);
        maxInput.setMinWidth(50);
        maxInput.setPrefWidth(80);

        Region minMaxSeparator = new Region();
        HBox.setHgrow(minMaxSeparator, Priority.ALWAYS);

        HBox minMaxContainer = new HBox(Views.DEFAULT_SPACING);
        minMaxContainer.setAlignment(Pos.BASELINE_CENTER);
        minMaxContainer.getChildren().addAll(minLabel, minInput, minMaxSeparator, maxInput, maxLabel);

        rangeSlider = createRangeSlider(minInput, maxInput);

        VBox rangeGroup = createGroup(
                labelAndReset,
                rangeSlider,
                minMaxContainer);

        // post-processing

        hillShading = new CheckBox("Enable hill-shading");
        hillShading.selectedProperty().addListener(this::onFilterOptionChange);

        smoothing = new CheckBox("Enable smoothing");
        smoothing.selectedProperty().addListener(this::onFilterOptionChange);

        analyticSignal = new CheckBox("Analytic signal");
        reductionToPole = new CheckBox("Reduction to pole");
        reductionToPole.selectedProperty().addListener((observable, oldValue, selected) -> {
            if (selected) {
                analyticSignal.setSelected(false);
            }
            onFilterOptionChange(observable, oldValue, selected);
        });
        analyticSignal.selectedProperty().addListener((observable, oldValue, selected) -> {
            if (selected) {
                reductionToPole.setSelected(false);
            }
            onFilterOptionChange(observable, oldValue, selected);
        });

        InputWithTopLabel rtpInclination = new InputWithTopLabel("RTP inclination (degrees)");
        rtpInclinationInput = rtpInclination.getInput();
        rtpInclinationInput.setText("60");
        rtpInclinationInput.textProperty().addListener(this::onRtpDirectionChange);

        InputWithTopLabel rtpDeclination = new InputWithTopLabel("RTP declination (degrees)");
        rtpDeclinationInput = rtpDeclination.getInput();
        rtpDeclinationInput.setText("0");
        rtpDeclinationInput.textProperty().addListener(this::onRtpDirectionChange);

        InputWithTopLabel rtpFallbackDate = new InputWithTopLabel("RTP fallback date, UTC");
        rtpFallbackDateInput = rtpFallbackDate.getInput();
        rtpFallbackDateInput.setPromptText("YYYY-MM-DD when samples have no timestamps");

        Button useIgrfDirection = new Button("Use IGRF direction");
        useIgrfDirection.setOnAction(event -> updateRtpDirection());

        VBox postProcessingGroup = createGroup(
                hillShading,
                smoothing,
                analyticSignal,
                reductionToPole,
                rtpInclination,
                rtpDeclination,
                rtpFallbackDate,
                useIgrfDirection
        );

        inputContainer.getChildren().setAll(
                warning,
                gridSelectionGroup,
                inputGroup,
                rangeGroup,
                paletteGroup,
                postProcessingGroup);

        showApply(true);
        showApplyToAll(true);
    }

    private VBox createGroup(Node... children) {
        VBox group = new VBox(Views.DEFAULT_SPACING, children);
        group.getStyleClass().add("group");
        VBox.setMargin(group,  new Insets(Views.DEFAULT_SPACING, 0, Views.DEFAULT_SPACING, 0));
        return group;
    }

    @Override
    public boolean isVisibleFor(SgyFile file) {
        return file instanceof CsvFile || file instanceof SonarFile || file instanceof NmeaFile;
    }

    private ComboBox<PaletteType> createPaletteSelector() {
        ComboBox<PaletteType> paletteSelector = new ComboBox<>();
        paletteSelector.setMaxWidth(Double.MAX_VALUE);
        paletteSelector.getItems().addAll(PaletteType.values());
        paletteSelector.setValue(PaletteType.defaultPaletteType());

        paletteSelector.setOnAction(e -> {
            if (!ignoreFilterEvents.get()) {
                applyFilter();
            }
        });
        return paletteSelector;
    }

    private ComboBox<SpectrumType> createSpectrumSelector() {
        ComboBox<SpectrumType> spectrumSelector = new ComboBox<>();
        spectrumSelector.setMaxWidth(Double.MAX_VALUE);
        spectrumSelector.getItems().addAll(SpectrumType.values());
        spectrumSelector.setValue(SpectrumType.defaultSpectrumType());

        spectrumSelector.setOnAction(e -> {
            if (!ignoreFilterEvents.get()) {
                applyFilter();
            }
        });
        return spectrumSelector;
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

        minInput.focusedProperty().addListener((observable, oldValue, newValue) -> {
           if (!newValue) {
               Double low = Text.parseDouble(minInput.getText());
               if (low != null) {
                   setRangeSliderLow(low);
               }
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

        maxInput.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue) {
                Double high = Text.parseDouble(maxInput.getText());
                if (high != null) {
                    setRangeSliderHigh(high);
                }
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
        boolean disable = params == null;
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
        String seriesName = getInputSeries();
        GriddingResult griddingResult = gridLayer.getResult(file, seriesName);
        if (griddingResult == null) {
            return false;
        }
        return !Objects.equals(getParams(), griddingResult.params());
    }

    private void showParamsChangedWarning(boolean show) {
        Platform.runLater(() -> {
            warning.setVisible(show);
            warning.setManaged(show);
        });
    }

    private String getInputSeries() {
        return inputSeriesSelector.getValue();
    }

    private void updateInputSeries() {
        String selectedSeries = inputSeriesSelector.getValue();
        updatingGridSelectors = true;
        try {
            inputSeriesSelector.getItems().clear();
            ColumnSchema schema = GeoData.getSchema(selectedFile != null ? selectedFile.getGeoData() : null);
            if (schema != null) {
                inputSeriesSelector.getItems().addAll(schema.getDisplayHeaders());
            }
            if (selectedSeries != null && inputSeriesSelector.getItems().contains(selectedSeries)) {
                inputSeriesSelector.setValue(selectedSeries);
            } else {
                String chartSeries = model.getSelectedSeriesName(selectedFile);
                inputSeriesSelector.setValue(inputSeriesSelector.getItems().contains(chartSeries)
                        ? chartSeries
                        : inputSeriesSelector.getItems().isEmpty() ? null : inputSeriesSelector.getItems().getFirst());
            }
        } finally {
            updatingGridSelectors = false;
        }
    }

    private void updateDisplayedGridSelector() {
        SgyFile file = selectedFile;
        String displayedSeries = gridLayer.getDisplayedSeries(file);
        updatingGridSelectors = true;
        try {
            displayedGridSelector.getItems().clear();
            for (GriddingResult result : gridLayer.getResults(file)) {
                displayedGridSelector.getItems().add(result.seriesName());
            }
            displayedGridSelector.setValue(displayedGridSelector.getItems().contains(displayedSeries)
                    ? displayedSeries : null);
        } finally {
            updatingGridSelectors = false;
        }
    }

    private void onInputSeriesSelected() {
        if (updatingGridSelectors) {
            return;
        }
        SgyFile file = selectedFile;
        String seriesName = getInputSeries();
        if (file == null || Strings.isNullOrEmpty(seriesName)) {
            return;
        }
        GriddingResult result = gridLayer.getResult(file, seriesName);
        updateParamInputs(result != null ? result.params() : griddingSettings.loadParams(file));
        updateFilterInputs(loadFilter(file, seriesName));
        onInputChange();
    }

    private void onDisplayedGridSelected() {
        if (!updatingGridSelectors) {
            gridLayer.setDisplayedSeries(selectedFile, displayedGridSelector.getValue());
        }
    }

    @Override
    public void updateView() {
        updateInputSeries();
        updateDisplayedGridSelector();
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
        return getSeriesRange(file, getInputSeries());
    }

    private @Nullable Range getSeriesRange(SgyFile file, String seriesName) {
        if (file == null || Strings.isNullOrEmpty(seriesName)) {
            return null;
        }

        Range range = null;
        for (SensorLineChart chart : model.getSensorCharts()) {
            if (Templates.equals(file, chart.getFile())) {
                Range chartRange = chart.getSeriesRange(seriesName);
                if (chartRange == null) {
                    continue;
                }
                range = range != null
                        ? range.union(chartRange)
                        : chartRange;
            }
        }
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
        if (range == null) {
            return;
        }

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
        SgyFile file = selectedFile;
        updateInputSeries();
        updateDisplayedGridSelector();
        String seriesName = getInputSeries();

        GriddingParams params = loadParams(file, seriesName);
        updateParamInputs(params);

        GriddingFilter filter = loadFilter(file, seriesName);
        updateFilterInputs(filter);
        if (!filter.reductionToPole()) {
            updateRtpDirection();
        }
    }

    private GriddingParams loadParams(SgyFile file, String seriesName) {
        GriddingResult result = gridLayer.getResult(file, seriesName);
        GriddingParams params = result != null ? result.params() : null;
        if (params != null) {
            return params;
        }
        return griddingSettings.loadParams(file);
    }

    // filter of the grid layer takes precedence over the saved one,
    // as it can be changed outside of this tool (e.g. over MCP)
    private GriddingFilter loadFilter(SgyFile file, String seriesName) {
        GriddingFilter filter = gridLayer.getFilter(file, seriesName);
        if (filter != null) {
            return filter;
        }
        Range seriesRange = getSeriesRange(file, seriesName);
        if (seriesRange == null) {
            // keep current input
            seriesRange = getFilter().range();
        }
        return griddingSettings.loadFilter(file, seriesName, seriesRange);
    }

    private void updateParamInputs(GriddingParams params) {
        if (params == null) {
            return;
        }

        cellSizeInput.setText(String.valueOf(params.cellSize()));
        blankingDistanceInput.setText(String.valueOf(params.blankingDistance()));
    }

    private void updateFilterInputs(GriddingFilter filter) {
        if (filter == null || Objects.equals(filter, getFilter())) {
            return;
        }

        ignoreFilterEvents.set(true);
        try {
            analyticSignal.setSelected(filter.analyticSignal());
            reductionToPole.setSelected(filter.reductionToPole());
            rtpInclinationInput.setText(Text.formatNumber(filter.rtpInclination()));
            rtpDeclinationInput.setText(Text.formatNumber(filter.rtpDeclination()));
            hillShading.setSelected(filter.hillShading());
            smoothing.setSelected(filter.smoothing());
            paletteSelector.setValue(filter.paletteType());
            spectrumSelector.setValue(filter.spectrumType());
            updateRangeSlider(filter.range());
        } finally {
            ignoreFilterEvents.set(false);
        }
    }

    @Override
    public void savePreferences() {
        SgyFile file = selectedFile;
        String seriesName = getInputSeries();

        griddingSettings.saveParams(file, getParams());
        griddingSettings.saveFilter(file, seriesName, getFilter());
    }

    @Override
    protected void onApply(ActionEvent event) {
        SgyFile file = selectedFile;
        if (file == null) {
            return;
        }
        String seriesName = getInputSeries();
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
        String seriesName = getInputSeries();
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
        if (cellSize == null || !Double.isFinite(cellSize) || cellSize <= 0.0
                || blankingDistance == null || !Double.isFinite(blankingDistance) || blankingDistance <= 0.0) {
            return null;
        }
        return new GriddingParams(
                cellSize,
                blankingDistance
        );
    }

    private GriddingFilter getFilter() {
        PaletteType paletteType = paletteSelector.getValue();
        if (paletteType == null) {
            paletteType = PaletteType.defaultPaletteType();
        }
        SpectrumType spectrumType = spectrumSelector.getValue();
        if (spectrumType == null) {
            spectrumType = SpectrumType.defaultSpectrumType();
        }
        return new GriddingFilter(
                new Range(rangeSlider.getLowValue(), rangeSlider.getHighValue()),
                analyticSignal.isSelected(),
                reductionToPole.isSelected(),
                getRtpDirection(rtpInclinationInput, 60.0),
                getRtpDirection(rtpDeclinationInput, 0.0),
                hillShading.isSelected(),
                smoothing.isSelected(),
                paletteType,
                spectrumType
        );
    }

    private void onRtpDirectionChange(ObservableValue<? extends String> observable, String oldValue, String newValue) {
        if (!ignoreFilterEvents.get() && reductionToPole.isSelected()) {
            applyFilter();
        }
    }

    private void updateRtpDirection() {
        if (selectedFile == null) {
            return;
        }
        try {
            RtpDirection direction = rtpDirectionService.derive(selectedFile.getGeoData(), parseRtpFallbackTimestamp());
            ignoreFilterEvents.set(true);
            try {
                rtpInclinationInput.setText(Text.formatNumber(direction.inclination()));
                rtpDeclinationInput.setText(Text.formatNumber(direction.declination()));
            } finally {
                ignoreFilterEvents.set(false);
            }
        } catch (IllegalArgumentException e) {
            Dialogs.showError("RTP direction", e.getMessage());
        }
    }

    private Instant parseRtpFallbackTimestamp() {
        String text = rtpFallbackDateInput.getText().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(text).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static double getRtpDirection(TextField input, double defaultValue) {
        try {
            double value = Double.parseDouble(input.getText());
            return Double.isFinite(value) ? value : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private void publishFilter() {
        SgyFile file = selectedFile;
        String seriesName = getInputSeries();

        GriddingFilter filter = getFilter();
        gridLayer.setFilter(file, seriesName, filter);
    }

    private void applyFilter() {
        savePreferences();
        publishFilter();
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
        SgyFile file = event.getFile();
        if (Objects.equals(selectedFile, file)) {
            Platform.runLater(() -> {
                String seriesName = model.getSelectedSeriesName(file);
                if (inputSeriesSelector.getItems().contains(seriesName)) {
                    inputSeriesSelector.setValue(seriesName);
                }
            });
        }
    }

    @EventListener
    private void onChange(WhatChanged changed) {
        if (changed.isTraceCut() || changed.isTraceValues()) {
            showParamsChangedWarning(true);
        }
    }

    @EventListener
    private void onGridUpdated(GridUpdatedEvent event) {
        // sync inputs with the actual grid state, so that grids
        // built outside of this tool (e.g. over MCP) are reflected
        SgyFile file = event.getFile();
        if (event.getGrid() == null || !Objects.equals(file, selectedFile)) {
            return;
        }
        // grid state is read in the FX thread, where filters
        // published by this tool always match its inputs
        Platform.runLater(() -> {
            if (!Objects.equals(file, selectedFile)) {
                return;
            }
            GriddingResult result = gridLayer.getResult(file, getInputSeries());
            updateParamInputs(result != null ? result.params() : null);
            updateFilterInputs(gridLayer.getFilter(file, getInputSeries()));
            updateDisplayedGridSelector();
        });
    }

    @EventListener
    private void onSeriesUpdated(SeriesUpdatedEvent event) {
        SgyFile file = selectedFile;
        if (!Objects.equals(file, event.getFile())) {
            return;
        }
        Platform.runLater(() -> {
            if (!Objects.equals(file, selectedFile)) {
                return;
            }
            updateInputSeries();
            if (event.isSeriesSelected() && inputSeriesSelector.getItems().contains(event.getSeriesName())) {
                inputSeriesSelector.setValue(event.getSeriesName());
            }
        });

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
