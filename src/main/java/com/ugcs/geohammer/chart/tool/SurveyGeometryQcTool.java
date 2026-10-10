package com.ugcs.geohammer.chart.tool;

import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.format.nmea.NmeaFile;
import com.ugcs.geohammer.format.svlog.SonarFile;
import com.ugcs.geohammer.map.layer.SurveyLineFamilyLayer;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
import com.ugcs.geohammer.service.quality.SurveyGeometryReport;
import com.ugcs.geohammer.service.quality.SurveyGeometryService;
import com.ugcs.geohammer.util.Text;
import com.ugcs.geohammer.view.Views;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;

@Component
public class SurveyGeometryQcTool extends FilterToolView {

    private final SurveyGeometryService surveyGeometryService;

    private final SurveyLineFamilyLayer surveyLineFamilyLayer;

    private final Label lineCount = new Label();

    private final Label sampleSpacing = new Label();

    private final Label primaryLineSpacing = new Label();

    private final Label tieLineSpacing = new Label();

    private final Label heading = new Label();

    private final Label previewSpacing = new Label();

    private final TextField previewSpacingInput = new TextField();

    private final Button usePreviewButton = new Button("Use preview");

    private final CheckBox showLineFamilies = new CheckBox("Show line families on map");

    private final Label suggestedCellSize = new Label();

    private final Label suggestedBlankingDistance = new Label();

    private final Label recommendationNote = new Label();

    private final HistogramView sampleHistogram = new HistogramView();

    private final HistogramView primaryLineHistogram = new HistogramView();

    private final ProjectedOffsetDensityView tieLineDensity = new ProjectedOffsetDensityView();

    private final Slider tieLineSpacingSlider = new Slider();

    private double detectedTieLineSpacing = Double.NaN;

    private double selectedTieLineSpacing = Double.NaN;

    private final HistogramView headingHistogram = new HistogramView(0.0, 360.0);

    public SurveyGeometryQcTool(SurveyGeometryService surveyGeometryService, SurveyLineFamilyLayer surveyLineFamilyLayer,
                                ExecutorService executor) {
        super(executor);
        this.surveyGeometryService = surveyGeometryService;
        this.surveyLineFamilyLayer = surveyLineFamilyLayer;

        applyButton.setText("Analyze survey geometry");
        showApplyToAll(false);
        disableActions(false);

        GridPane metrics = new GridPane();
        metrics.setHgap(8);
        metrics.setVgap(4);
        addMetric(metrics, 0, "Lines", lineCount);
        addMetric(metrics, 1, "Sample spacing", sampleSpacing);
        addMetric(metrics, 2, "Primary-line spacing", primaryLineSpacing);
        addMetric(metrics, 3, "Tie-line spacing", tieLineSpacing);
        addMetric(metrics, 4, "Line headings", heading);

        GridPane recommendation = new GridPane();
        recommendation.setHgap(8);
        recommendation.setVgap(4);
        addMetric(recommendation, 0, "Suggested cell size", suggestedCellSize);
        addMetric(recommendation, 1, "Suggested blanking distance", suggestedBlankingDistance);
        configureGrid(metrics);
        configureGrid(recommendation);

        recommendationNote.getStyleClass().add("dim");
        recommendationNote.setWrapText(true);
        VBox recommendationGroup = new VBox(Views.DEFAULT_SPACING,
                new Label("Suggested gridding"), recommendation, recommendationNote);
        recommendationGroup.getStyleClass().add("group");
        inputContainer.getChildren().setAll(
                metrics,
                showLineFamilies,
                createHistogram("Along-line sample spacing (m)", sampleHistogram),
                createHistogram("Primary-line spacing (m)", primaryLineHistogram),
                createHistogram("Tie-line projected-offset density (m)", tieLineDensity),
                createSpacingPreview(),
                createHistogram("Segment heading (degrees)", headingHistogram),
                recommendationGroup);
        tieLineSpacingSlider.valueProperty().addListener((observable, oldValue, newValue) -> updateSpacingPreview());
        previewSpacingInput.setOnAction(e -> onPreviewSpacingEntered());
        usePreviewButton.setOnAction(e -> usePreviewSpacing());
        showLineFamilies.setOnAction(e -> updateLineFamilyOverlay());
        clearReport();
    }

    @Override
    public boolean isVisibleFor(SgyFile file) {
        return file instanceof CsvFile || file instanceof SonarFile || file instanceof NmeaFile;
    }

    @Override
    public void updateView() {
        clearReport();
    }

    @Override
    protected void onApply(ActionEvent event) {
        SgyFile file = selectedFile;
        if (file == null) {
            return;
        }
        submitAction(() -> {
            SurveyGeometryReport report = surveyGeometryService.analyze(file.getGeoData());
            Platform.runLater(() -> showReport(report));
            return report;
        });
    }

    private static void addMetric(GridPane metrics, int row, String name, Label value) {
        value.setMaxWidth(Double.MAX_VALUE);
        value.setWrapText(true);
        metrics.add(new Label(name), 0, row);
        metrics.add(value, 1, row);
    }

    private static void configureGrid(GridPane grid) {
        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setHgrow(Priority.ALWAYS);
        valueColumn.setFillWidth(true);
        grid.getColumnConstraints().addAll(new ColumnConstraints(), valueColumn);
    }

    private static VBox createHistogram(String title, Region histogram) {
        Label label = new Label(title);
        VBox box = new VBox(Views.DEFAULT_SPACING, label, histogram);
        box.getStyleClass().add("group");
        return box;
    }

    private HBox createSpacingPreview() {
        tieLineSpacingSlider.setMaxWidth(Double.MAX_VALUE);
        tieLineSpacingSlider.setBlockIncrement(50.0);
        tieLineSpacingSlider.setMajorTickUnit(1_000.0);
        tieLineSpacingSlider.setShowTickMarks(true);
        previewSpacingInput.setPromptText("m");
        previewSpacingInput.setPrefColumnCount(5);
        HBox box = new HBox(Views.DEFAULT_SPACING, new Label("Preview spacing"), tieLineSpacingSlider,
                previewSpacing, previewSpacingInput, usePreviewButton);
        HBox.setHgrow(tieLineSpacingSlider, Priority.ALWAYS);
        box.getStyleClass().add("group");
        return box;
    }

    private void showReport(SurveyGeometryReport report) {
        lineCount.setText(report.lineCount() + " (" + report.primaryLineCount() + " primary, "
                + report.tieLineCount() + " tie)");
        sampleSpacing.setText(formatDistribution(report.sampleSpacing(), "m"));
        primaryLineSpacing.setText(formatDistribution(report.primaryLineSpacing(), "m"));
        tieLineSpacing.setText(formatTieLinePeaks(report));
        detectedTieLineSpacing = report.tieLinePeakSpacing();
        selectedTieLineSpacing = Double.NaN;
        heading.setText(formatLineHeadings(report));
        sampleHistogram.setValues(report.sampleSpacingValues());
        primaryLineHistogram.setValues(report.primaryLineSpacingValues());
        tieLineDensity.setDensity(report.tieLineDensityPositions(), report.tieLineDensityValues());
        surveyLineFamilyLayer.setLineFamilies(selectedFile, report.primaryLinePaths(), report.tieLinePaths());
        showLineFamilies.setSelected(true);
        updateLineFamilyOverlay();
        configureSpacingPreview(report);
        headingHistogram.setValues(report.headingValues());
        suggestedCellSize.setText(format(report.recommendedCellSize()) + " m");
        suggestedBlankingDistance.setText(format(report.recommendedBlankingDistance()) + " m");
        recommendationNote.setText("Cell size uses one-quarter of median primary-line spacing. "
                + "Values are not applied automatically.");
    }

    private void clearReport() {
        lineCount.setText("Analyze the selected survey");
        sampleSpacing.setText("n/a");
        primaryLineSpacing.setText("n/a");
        tieLineSpacing.setText("n/a");
        heading.setText("n/a");
        sampleHistogram.setValues(java.util.List.of());
        primaryLineHistogram.setValues(java.util.List.of());
        tieLineDensity.setDensity(java.util.List.of(), java.util.List.of());
        tieLineSpacingSlider.setDisable(true);
        previewSpacingInput.setDisable(true);
        usePreviewButton.setDisable(true);
        detectedTieLineSpacing = Double.NaN;
        selectedTieLineSpacing = Double.NaN;
        previewSpacing.setText("n/a");
        previewSpacingInput.clear();
        showLineFamilies.setSelected(false);
        surveyLineFamilyLayer.clear();
        headingHistogram.setValues(java.util.List.of());
        suggestedCellSize.setText("n/a");
        suggestedBlankingDistance.setText("n/a");
        recommendationNote.setText("Uses mapped latitude, longitude, and line fields.");
    }

    private static String formatDistribution(SurveyGeometryReport.Distribution distribution, String unit) {
        if (distribution.isEmpty()) {
            return "n/a";
        }
        return "median " + format(distribution.median()) + " " + unit
                + " (Q1–Q3 " + format(distribution.lowerQuartile()) + "–"
                + format(distribution.upperQuartile()) + ")";
    }

    private static String formatLineHeadings(SurveyGeometryReport report) {
        if (!Double.isFinite(report.primaryLineOrientation())) {
            return "n/a";
        }
        String result = "primary " + formatHeading(report.primaryLineOrientation());
        if (Double.isFinite(report.tieLineOrientation())) {
            result += "; tie " + formatHeading(report.tieLineOrientation());
        }
        return result;
    }

    private static String formatHeading(double heading) {
        return format(heading) + "°/" + format((heading + 180.0) % 360.0) + "°";
    }

    private static String formatTieLinePeaks(SurveyGeometryReport report) {
        if (!Double.isFinite(report.tieLinePeakSpacing())) {
            return "n/a";
        }
        return format(report.tieLinePeakSpacing()) + " m (" + report.tieLinePeakCount() + " peaks)";
    }

    private void configureSpacingPreview(SurveyGeometryReport report) {
        if (report.tieLineDensityPositions().size() < 2 || !Double.isFinite(report.tieLinePeakSpacing())) {
            tieLineSpacingSlider.setDisable(true);
            previewSpacingInput.setDisable(true);
            usePreviewButton.setDisable(true);
            previewSpacing.setText("n/a");
            return;
        }
        double range = report.tieLineDensityPositions().getLast() - report.tieLineDensityPositions().getFirst();
        double minimum = Double.isFinite(report.primaryLineSpacing().median())
                ? report.primaryLineSpacing().median()
                : 100.0;
        tieLineSpacingSlider.setMin(Math.max(10.0, minimum));
        tieLineSpacingSlider.setMax(Math.max(minimum * 2.0, range / 2.0));
        tieLineSpacingSlider.setValue(Math.clamp(report.tieLinePeakSpacing(), tieLineSpacingSlider.getMin(),
                tieLineSpacingSlider.getMax()));
        tieLineSpacingSlider.setDisable(false);
        previewSpacingInput.setDisable(false);
        usePreviewButton.setDisable(false);
        updateSpacingPreview();
    }

    private void updateSpacingPreview() {
        if (tieLineSpacingSlider.isDisabled()) {
            return;
        }
        double spacing = tieLineSpacingSlider.getValue();
        previewSpacing.setText(format(spacing) + " m");
        previewSpacingInput.setText(format(spacing));
        if (Double.isFinite(selectedTieLineSpacing) && Math.abs(selectedTieLineSpacing - spacing) > 1e-6) {
            selectedTieLineSpacing = Double.NaN;
            tieLineSpacing.setText(formatDetectedTieLineSpacing());
        }
        tieLineDensity.setPreviewSpacing(spacing);
    }

    private void onPreviewSpacingEntered() {
        String input = previewSpacingInput.getText().replace(",", "").trim();
        try {
            double spacing = Double.parseDouble(input);
            if (!Double.isFinite(spacing) || spacing <= 0.0) {
                throw new NumberFormatException();
            }
            tieLineSpacingSlider.setMin(Math.min(tieLineSpacingSlider.getMin(), spacing));
            tieLineSpacingSlider.setMax(Math.max(tieLineSpacingSlider.getMax(), spacing));
            tieLineSpacingSlider.setValue(spacing);
        } catch (NumberFormatException exception) {
            previewSpacingInput.setText(format(tieLineSpacingSlider.getValue()));
        }
    }

    private void usePreviewSpacing() {
        selectedTieLineSpacing = tieLineSpacingSlider.getValue();
        tieLineSpacing.setText(format(selectedTieLineSpacing) + " m (selected preview)");
    }

    private void updateLineFamilyOverlay() {
        surveyLineFamilyLayer.setActive(showLineFamilies.isSelected());
        surveyLineFamilyLayer.repaint();
    }

    private String formatDetectedTieLineSpacing() {
        if (!Double.isFinite(detectedTieLineSpacing)) {
            return "n/a";
        }
        return format(detectedTieLineSpacing) + " m (automatic estimate)";
    }

    private static String format(double value) {
        if (!Double.isFinite(value)) {
            return "n/a";
        }
        double magnitude = Math.abs(value);
        int fractionDigits = magnitude >= 100.0 ? 0 : magnitude >= 10.0 ? 1 : 2;
        return Text.createNumberFormat(0, fractionDigits).format(value);
    }

    @EventListener
    private void onFileSelected(FileSelectedEvent event) {
        Platform.runLater(() -> selectFile(event.getFile()));
    }
}
