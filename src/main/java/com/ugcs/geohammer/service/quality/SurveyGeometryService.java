package com.ugcs.geohammer.service.quality;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.model.LatLon;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class SurveyGeometryService {

    public SurveyGeometryReport analyze(List<GeoData> data) {
        List<LineGeometry> lines = collectLines(data);
        List<Double> sampleSpacing = new ArrayList<>();
        List<Double> headings = new ArrayList<>();
        for (LineGeometry line : lines) {
            collectSegments(line.points(), sampleSpacing, headings);
        }

        List<Double> lineSpacing = collectLineSpacing(lines);
        SurveyGeometryReport.Distribution sampleSummary = summarize(sampleSpacing);
        SurveyGeometryReport.Distribution lineSummary = summarize(lineSpacing);

        double cellSize = recommendCellSize(sampleSummary, lineSummary);
        double blankingDistance = recommendBlankingDistance(cellSize, lineSummary);
        return new SurveyGeometryReport(lines.size(), sampleSummary, lineSummary, summarize(headings),
                List.copyOf(sampleSpacing), List.copyOf(lineSpacing), List.copyOf(headings),
                cellSize, blankingDistance);
    }

    private static List<LineGeometry> collectLines(List<GeoData> data) {
        List<LineGeometry> lines = new ArrayList<>();
        List<LatLon> points = new ArrayList<>();
        Integer currentLine = null;
        for (GeoData value : data) {
            if (value == null) {
                continue;
            }
            Integer line = value.getLineOrDefault(0);
            if (currentLine != null && !line.equals(currentLine)) {
                addLine(lines, points);
                points = new ArrayList<>();
            }
            currentLine = line;
            LatLon point = value.getLatLon();
            if (point != null) {
                points.add(point);
            }
        }
        addLine(lines, points);
        return lines;
    }

    private static void addLine(List<LineGeometry> lines, List<LatLon> points) {
        if (points.size() < 2) {
            return;
        }
        double latitude = 0.0;
        double longitude = 0.0;
        for (LatLon point : points) {
            latitude += point.getLatDgr();
            longitude += point.getLonDgr();
        }
        lines.add(new LineGeometry(points, new LatLon(latitude / points.size(), longitude / points.size())));
    }

    private static void collectSegments(List<LatLon> points, List<Double> sampleSpacing, List<Double> headings) {
        for (int i = 1; i < points.size(); i++) {
            LatLon previous = points.get(i - 1);
            LatLon current = points.get(i);
            double distance = previous.getDistance(current);
            if (!Double.isFinite(distance) || distance <= 0.0) {
                continue;
            }
            sampleSpacing.add(distance);
            headings.add(previous.getBearing(current));
        }
    }

    private static List<Double> collectLineSpacing(List<LineGeometry> lines) {
        List<Double> spacing = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            double nearest = Double.POSITIVE_INFINITY;
            LatLon center = lines.get(i).center();
            for (int j = 0; j < lines.size(); j++) {
                if (i == j) {
                    continue;
                }
                nearest = Math.min(nearest, center.getDistance(lines.get(j).center()));
            }
            if (Double.isFinite(nearest) && nearest > 0.0) {
                spacing.add(nearest);
            }
        }
        return spacing;
    }

    private static SurveyGeometryReport.Distribution summarize(List<Double> values) {
        if (values.isEmpty()) {
            return new SurveyGeometryReport.Distribution(0, Double.NaN, Double.NaN, Double.NaN,
                    Double.NaN, Double.NaN);
        }
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        return new SurveyGeometryReport.Distribution(sorted.size(), sorted.getFirst(), percentile(sorted, 0.25),
                percentile(sorted, 0.5), percentile(sorted, 0.75), sorted.getLast());
    }

    private static double percentile(List<Double> sorted, double percentile) {
        double index = percentile * (sorted.size() - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        return sorted.get(lower) + (sorted.get(upper) - sorted.get(lower)) * (index - lower);
    }

    private static double recommendCellSize(SurveyGeometryReport.Distribution sampleSpacing,
                                            SurveyGeometryReport.Distribution lineSpacing) {
        if (sampleSpacing.isEmpty()) {
            return Double.NaN;
        }
        double recommended = sampleSpacing.median() / 2.0;
        if (!lineSpacing.isEmpty()) {
            recommended = Math.min(recommended, lineSpacing.median() / 4.0);
        }
        return roundToTwoSignificantDigits(recommended);
    }

    private static double recommendBlankingDistance(double cellSize,
                                                    SurveyGeometryReport.Distribution lineSpacing) {
        if (!Double.isFinite(cellSize)) {
            return Double.NaN;
        }
        double recommended = lineSpacing.isEmpty() ? cellSize * 4.0 : lineSpacing.median() * 2.0;
        return roundToTwoSignificantDigits(Math.max(recommended, cellSize));
    }

    private static double roundToTwoSignificantDigits(double value) {
        if (value <= 0.0 || !Double.isFinite(value)) {
            return Double.NaN;
        }
        double scale = Math.pow(10.0, Math.floor(Math.log10(value)) - 1.0);
        return Math.round(value / scale) * scale;
    }

    private record LineGeometry(List<LatLon> points, LatLon center) {
    }
}
