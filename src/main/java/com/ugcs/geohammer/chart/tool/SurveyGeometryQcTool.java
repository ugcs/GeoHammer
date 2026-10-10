package com.ugcs.geohammer.chart.tool;

import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.format.nmea.NmeaFile;
import com.ugcs.geohammer.format.svlog.SonarFile;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
import com.ugcs.geohammer.service.quality.SurveyGeometryReport;
import com.ugcs.geohammer.service.quality.SurveyGeometryService;
import com.ugcs.geohammer.util.Text;
import com.ugcs.geohammer.view.Views;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;

@Component
public class SurveyGeometryQcTool extends FilterToolView {

    private final SurveyGeometryService surveyGeometryService;

    private final Label lineCount = new Label();

    private final Label sampleSpacing = new Label();

    private final Label lineSpacing = new Label();

    private final Label heading = new Label();

    private final Label suggestedCellSize = new Label();

    private final Label suggestedBlankingDistance = new Label();

    private final Label recommendationNote = new Label();

    private final HistogramView sampleHistogram = new HistogramView();

    private final HistogramView lineHistogram = new HistogramView();

    private final HistogramView headingHistogram = new HistogramView(0.0, 360.0);

    public SurveyGeometryQcTool(SurveyGeometryService surveyGeometryService, ExecutorService executor) {
        super(executor);
        this.surveyGeometryService = surveyGeometryService;

        applyButton.setText("Analyze survey geometry");
        showApplyToAll(false);
        disableActions(false);

        GridPane metrics = new GridPane();
        metrics.setHgap(8);
        metrics.setVgap(4);
        addMetric(metrics, 0, "Lines", lineCount);
        addMetric(metrics, 1, "Sample spacing", sampleSpacing);
        addMetric(metrics, 2, "Line-centre separation", lineSpacing);
        addMetric(metrics, 3, "Heading", heading);

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
                createHistogram("Along-line sample spacing (m)", sampleHistogram),
                createHistogram("Nearest line-centre separation (m)", lineHistogram),
                createHistogram("Segment heading (degrees)", headingHistogram),
                recommendationGroup);
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

    private static VBox createHistogram(String title, HistogramView histogram) {
        Label label = new Label(title);
        VBox box = new VBox(Views.DEFAULT_SPACING, label, histogram);
        box.getStyleClass().add("group");
        return box;
    }

    private void showReport(SurveyGeometryReport report) {
        lineCount.setText(Integer.toString(report.lineCount()));
        sampleSpacing.setText(formatDistribution(report.sampleSpacing(), "m"));
        lineSpacing.setText(formatDistribution(report.lineSpacing(), "m"));
        heading.setText(formatHeading(report.heading()));
        sampleHistogram.setValues(report.sampleSpacingValues());
        lineHistogram.setValues(report.lineSpacingValues());
        headingHistogram.setValues(report.headingValues());
        suggestedCellSize.setText(format(report.recommendedCellSize()) + " m");
        suggestedBlankingDistance.setText(format(report.recommendedBlankingDistance()) + " m");
        recommendationNote.setText("Recommendations are not applied automatically.");
    }

    private void clearReport() {
        lineCount.setText("Analyze the selected survey");
        sampleSpacing.setText("n/a");
        lineSpacing.setText("n/a");
        heading.setText("n/a");
        sampleHistogram.setValues(java.util.List.of());
        lineHistogram.setValues(java.util.List.of());
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

    private static String formatHeading(SurveyGeometryReport.Distribution distribution) {
        if (distribution.isEmpty()) {
            return "n/a";
        }
        return "range " + format(distribution.minimum()) + "-" + format(distribution.maximum()) + " degrees";
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
