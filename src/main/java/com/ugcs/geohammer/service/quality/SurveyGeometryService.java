package com.ugcs.geohammer.service.quality;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.model.LatLon;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class SurveyGeometryService {

    private static final double ORIENTATION_TOLERANCE = 20.0;

    private static final double METERS_PER_DEGREE = 111_320.0;

    public SurveyGeometryReport analyze(List<GeoData> data) {
        List<LineGeometry> lines = collectLines(data);
        List<Double> sampleSpacing = new ArrayList<>();
        List<Double> headings = new ArrayList<>();
        for (LineGeometry line : lines) {
            collectSegments(line.points(), sampleSpacing, headings);
        }

        LineFamilies families = splitLineFamilies(lines);
        List<Double> primaryLineSpacing = collectLineSpacing(families.primary());
        List<Double> tieLineSpacing = collectLineSpacing(families.tieLines());
        SurveyGeometryReport.Distribution sampleSummary = summarize(sampleSpacing);
        SurveyGeometryReport.Distribution primarySummary = summarize(primaryLineSpacing);
        SurveyGeometryReport.Distribution tieSummary = summarize(tieLineSpacing);

        double cellSize = recommendCellSize(primarySummary);
        double blankingDistance = recommendBlankingDistance(primarySummary);
        return new SurveyGeometryReport(lines.size(), families.primary().lines().size(), families.tieLines().lines().size(),
                sampleSummary, primarySummary, tieSummary, summarize(headings),
                List.copyOf(sampleSpacing), List.copyOf(primaryLineSpacing), List.copyOf(tieLineSpacing),
                List.copyOf(headings),
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
        LatLon center = new LatLon(latitude / points.size(), longitude / points.size());
        double orientation = points.getFirst().getBearing(points.getLast()) % 180.0;
        lines.add(new LineGeometry(points, center, orientation));
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

    private static LineFamilies splitLineFamilies(List<LineGeometry> lines) {
        if (lines.isEmpty()) {
            return new LineFamilies(new LineFamily(List.of(), Double.NaN), new LineFamily(List.of(), Double.NaN));
        }
        LineGeometry dominant = lines.getFirst();
        int largestGroup = 0;
        for (LineGeometry candidate : lines) {
            int count = 0;
            for (LineGeometry line : lines) {
                if (orientationDifference(candidate.orientation(), line.orientation()) <= ORIENTATION_TOLERANCE) {
                    count++;
                }
            }
            if (count > largestGroup) {
                dominant = candidate;
                largestGroup = count;
            }
        }

        List<LineGeometry> primary = new ArrayList<>();
        List<LineGeometry> ties = new ArrayList<>();
        for (LineGeometry line : lines) {
            if (orientationDifference(dominant.orientation(), line.orientation()) <= ORIENTATION_TOLERANCE) {
                primary.add(line);
            } else {
                ties.add(line);
            }
        }
        return new LineFamilies(new LineFamily(primary, meanOrientation(primary)), new LineFamily(ties, meanOrientation(ties)));
    }

    private static double orientationDifference(double first, double second) {
        double difference = Math.abs(first - second);
        return Math.min(difference, 180.0 - difference);
    }

    private static double meanOrientation(List<LineGeometry> lines) {
        if (lines.isEmpty()) {
            return Double.NaN;
        }
        double x = 0.0;
        double y = 0.0;
        for (LineGeometry line : lines) {
            double angle = Math.toRadians(2.0 * line.orientation());
            x += Math.cos(angle);
            y += Math.sin(angle);
        }
        double orientation = Math.toDegrees(Math.atan2(y, x)) / 2.0;
        return orientation < 0.0 ? orientation + 180.0 : orientation;
    }

    private static List<Double> collectLineSpacing(LineFamily family) {
        List<Double> spacing = new ArrayList<>();
        if (family.lines().size() < 2 || !Double.isFinite(family.orientation())) {
            return spacing;
        }
        LatLon origin = family.lines().getFirst().center();
        List<Double> offsets = new ArrayList<>(family.lines().size());
        double orientation = Math.toRadians(family.orientation());
        double cosineLatitude = Math.cos(Math.toRadians(origin.getLatDgr()));
        for (LineGeometry line : family.lines()) {
            LatLon center = line.center();
            double east = (center.getLonDgr() - origin.getLonDgr()) * METERS_PER_DEGREE * cosineLatitude;
            double north = (center.getLatDgr() - origin.getLatDgr()) * METERS_PER_DEGREE;
            offsets.add(east * Math.cos(orientation) - north * Math.sin(orientation));
        }
        offsets.sort(Comparator.naturalOrder());
        for (int i = 1; i < offsets.size(); i++) {
            double distance = offsets.get(i) - offsets.get(i - 1);
            if (distance > 0.0) {
                spacing.add(distance);
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

    private static double recommendCellSize(SurveyGeometryReport.Distribution primaryLineSpacing) {
        if (primaryLineSpacing.isEmpty()) {
            return Double.NaN;
        }
        return primaryLineSpacing.median() / 4.0;
    }

    private static double recommendBlankingDistance(SurveyGeometryReport.Distribution primaryLineSpacing) {
        if (primaryLineSpacing.isEmpty()) {
            return Double.NaN;
        }
        return primaryLineSpacing.median() * 2.0;
    }

    private record LineGeometry(List<LatLon> points, LatLon center, double orientation) {
    }

    private record LineFamily(List<LineGeometry> lines, double orientation) {
    }

    private record LineFamilies(LineFamily primary, LineFamily tieLines) {
    }
}
