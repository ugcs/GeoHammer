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

    private static final double DEFAULT_OFFSET_BIN_WIDTH = 100.0;

    private static final double TIE_ORIENTATION_SEARCH_RANGE = 20.0;

    private static final double TIE_ORIENTATION_SEARCH_STEP = 0.5;

    public SurveyGeometryReport analyze(List<GeoData> data) {
        List<LineGeometry> lines = collectLines(data);
        List<Double> sampleSpacing = new ArrayList<>();
        List<Double> headings = new ArrayList<>();
        for (LineGeometry line : lines) {
            collectSegments(line.points(), sampleSpacing, headings);
        }

        LineFamilies families = splitLineFamilies(lines);
        List<Double> primaryOffsets = collectLineOffsets(families.primary());
        List<Double> primaryLineSpacing = collectAdjacentSpacing(primaryOffsets);
        SurveyGeometryReport.Distribution sampleSummary = summarize(sampleSpacing);
        SurveyGeometryReport.Distribution primarySummary = summarize(primaryLineSpacing);
        TieLinePeaks tiePeaks = findTieLinePeaks(families.tieLines(), primarySummary.median(),
                families.primary().orientation());

        double cellSize = recommendCellSize(primarySummary);
        double blankingDistance = recommendBlankingDistance(primarySummary);
        return new SurveyGeometryReport(lines.size(), families.primary().lines().size(), families.tieLines().lines().size(),
                families.primary().orientation(), tiePeaks.orientation(), toPaths(families.primary()),
                toPaths(families.tieLines()), sampleSummary, primarySummary, summarize(headings),
                List.copyOf(sampleSpacing), List.copyOf(primaryLineSpacing), List.copyOf(headings),
                tiePeaks.spacing(), tiePeaks.peakPositions().size(), tiePeaks.densityPositions(),
                tiePeaks.densityValues(), tiePeaks.peakPositions(),
                cellSize, blankingDistance);
    }

    private static List<List<LatLon>> toPaths(LineFamily family) {
        List<List<LatLon>> paths = new ArrayList<>(family.lines().size());
        for (LineGeometry line : family.lines()) {
            paths.add(List.copyOf(line.points()));
        }
        return List.copyOf(paths);
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

    private static List<Double> collectLineOffsets(LineFamily family) {
        List<Double> offsets = new ArrayList<>();
        if (family.lines().size() < 2 || !Double.isFinite(family.orientation())) {
            return offsets;
        }
        LatLon origin = family.lines().getFirst().center();
        double orientation = Math.toRadians(family.orientation());
        double cosineLatitude = Math.cos(Math.toRadians(origin.getLatDgr()));
        for (LineGeometry line : family.lines()) {
            List<Double> lineOffsets = new ArrayList<>(line.points().size());
            for (LatLon point : line.points()) {
                double east = (point.getLonDgr() - origin.getLonDgr()) * METERS_PER_DEGREE * cosineLatitude;
                double north = (point.getLatDgr() - origin.getLatDgr()) * METERS_PER_DEGREE;
                lineOffsets.add(east * Math.cos(orientation) - north * Math.sin(orientation));
            }
            offsets.add(median(lineOffsets));
        }
        offsets.sort(Comparator.naturalOrder());
        return offsets;
    }

    private static List<Double> collectAdjacentSpacing(List<Double> offsets) {
        List<Double> spacing = new ArrayList<>();
        for (int i = 1; i < offsets.size(); i++) {
            double distance = offsets.get(i) - offsets.get(i - 1);
            if (distance > 0.0) {
                spacing.add(distance);
            }
        }
        return spacing;
    }

    private static TieLinePeaks findTieLinePeaks(LineFamily tieLines, double primaryLineSpacing,
                                                 double primaryOrientation) {
        if (tieLines.lines().isEmpty()) {
            return TieLinePeaks.empty();
        }
        double orientation = findDominantTieOrientation(tieLines, primaryLineSpacing, primaryOrientation);
        List<Double> offsets = collectSampleOffsets(tieLines, orientation);
        if (offsets.size() < 2) {
            return new TieLinePeaks(orientation, Double.NaN, List.of(), List.of(), List.of());
        }
        double binWidth = Double.isFinite(primaryLineSpacing)
                ? Math.max(20.0, primaryLineSpacing / 4.0)
                : DEFAULT_OFFSET_BIN_WIDTH;
        double minimum = offsets.getFirst();
        double maximum = offsets.getLast();
        double origin = Math.floor(minimum / binWidth) * binWidth;
        int binCount = (int) Math.ceil((maximum - origin) / binWidth) + 1;
        int[] counts = new int[binCount];
        for (double offset : offsets) {
            int index = Math.min((int) ((offset - origin) / binWidth), binCount - 1);
            counts[Math.max(index, 0)]++;
        }

        int radius = Math.max(1, (int) Math.round(primaryLineSpacing / (2.0 * binWidth)));
        List<Double> densityPositions = new ArrayList<>(binCount);
        List<Double> densityValues = new ArrayList<>(binCount);
        double maximumDensity = 0.0;
        for (int i = 0; i < binCount; i++) {
            int sum = 0;
            for (int j = Math.max(0, i - radius); j <= Math.min(binCount - 1, i + radius); j++) {
                sum += counts[j];
            }
            double density = (double) sum / (2 * radius + 1);
            densityPositions.add(origin + (i + 0.5) * binWidth);
            densityValues.add(density);
            maximumDensity = Math.max(maximumDensity, density);
        }
        List<Double> peaks = findDensityPeaks(densityPositions, densityValues, maximumDensity, primaryLineSpacing,
                binWidth);
        List<Double> peakSpacing = collectAdjacentSpacing(peaks);
        double spacing = peakSpacing.isEmpty() ? Double.NaN : median(peakSpacing);
        return new TieLinePeaks(orientation, spacing, List.copyOf(densityPositions),
                List.copyOf(densityValues), List.copyOf(peaks));
    }

    private static double findDominantTieOrientation(LineFamily tieLines, double primaryLineSpacing,
                                                     double primaryOrientation) {
        double expected = Double.isFinite(primaryOrientation)
                ? normalizeOrientation(primaryOrientation + 90.0)
                : tieLines.orientation();
        if (!Double.isFinite(expected)) {
            return Double.NaN;
        }
        double binWidth = Double.isFinite(primaryLineSpacing)
                ? Math.max(20.0, primaryLineSpacing / 4.0)
                : DEFAULT_OFFSET_BIN_WIDTH;
        double bestOrientation = expected;
        long bestScore = Long.MIN_VALUE;
        for (double offset = -TIE_ORIENTATION_SEARCH_RANGE; offset <= TIE_ORIENTATION_SEARCH_RANGE;
             offset += TIE_ORIENTATION_SEARCH_STEP) {
            double candidate = normalizeOrientation(expected + offset);
            long score = calculateOrientationScore(tieLines, candidate, binWidth);
            if (score > bestScore) {
                bestScore = score;
                bestOrientation = candidate;
            }
        }
        return bestOrientation;
    }

    private static long calculateOrientationScore(LineFamily family, double orientation, double binWidth) {
        LatLon origin = family.lines().getFirst().center();
        double radians = Math.toRadians(orientation);
        double cosineLatitude = Math.cos(Math.toRadians(origin.getLatDgr()));
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        for (LineGeometry line : family.lines()) {
            for (LatLon point : line.points()) {
                double offset = projectOffset(point, origin, radians, cosineLatitude);
                minimum = Math.min(minimum, offset);
                maximum = Math.max(maximum, offset);
            }
        }
        int binCount = (int) Math.ceil((maximum - minimum) / binWidth) + 1;
        int[] counts = new int[binCount];
        for (LineGeometry line : family.lines()) {
            for (LatLon point : line.points()) {
                double offset = projectOffset(point, origin, radians, cosineLatitude);
                int index = Math.min((int) ((offset - minimum) / binWidth), binCount - 1);
                counts[Math.max(index, 0)]++;
            }
        }
        long score = 0;
        for (int count : counts) {
            score += (long) count * count;
        }
        return score;
    }

    private static List<Double> collectSampleOffsets(LineFamily family, double orientation) {
        List<Double> offsets = new ArrayList<>();
        if (family.lines().isEmpty() || !Double.isFinite(orientation)) {
            return offsets;
        }
        LatLon origin = family.lines().getFirst().center();
        double radians = Math.toRadians(orientation);
        double cosineLatitude = Math.cos(Math.toRadians(origin.getLatDgr()));
        for (LineGeometry line : family.lines()) {
            for (LatLon point : line.points()) {
                offsets.add(projectOffset(point, origin, radians, cosineLatitude));
            }
        }
        offsets.sort(Comparator.naturalOrder());
        return offsets;
    }

    private static double projectOffset(LatLon point, LatLon origin, double orientation, double cosineLatitude) {
        double east = (point.getLonDgr() - origin.getLonDgr()) * METERS_PER_DEGREE * cosineLatitude;
        double north = (point.getLatDgr() - origin.getLatDgr()) * METERS_PER_DEGREE;
        return east * Math.cos(orientation) - north * Math.sin(orientation);
    }

    private static double normalizeOrientation(double orientation) {
        double normalized = orientation % 180.0;
        return normalized < 0.0 ? normalized + 180.0 : normalized;
    }

    private static List<Double> findDensityPeaks(List<Double> positions, List<Double> densityValues,
                                                  double maximumDensity, double primaryLineSpacing,
                                                  double binWidth) {
        List<Peak> candidates = new ArrayList<>();
        double threshold = maximumDensity * 0.1;
        for (int i = 0; i < densityValues.size(); i++) {
            double value = densityValues.get(i);
            double previous = i > 0 ? densityValues.get(i - 1) : Double.NEGATIVE_INFINITY;
            double next = i + 1 < densityValues.size() ? densityValues.get(i + 1) : Double.NEGATIVE_INFINITY;
            if (value >= threshold && value >= previous && value > next) {
                candidates.add(new Peak(positions.get(i), value));
            }
        }
        candidates.sort((first, second) -> Double.compare(second.density(), first.density()));

        double minimumPeakDistance = Double.isFinite(primaryLineSpacing)
                ? primaryLineSpacing * 3.0
                : binWidth * 4.0;
        List<Double> peaks = new ArrayList<>();
        for (Peak candidate : candidates) {
            boolean separated = true;
            for (double peak : peaks) {
                if (Math.abs(candidate.position() - peak) < minimumPeakDistance) {
                    separated = false;
                    break;
                }
            }
            if (separated) {
                peaks.add(candidate.position());
            }
        }
        peaks.sort(Comparator.naturalOrder());
        return peaks;
    }

    private static double median(List<Double> values) {
        values.sort(Comparator.naturalOrder());
        return percentile(values, 0.5);
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

    private record Peak(double position, double density) {
    }

    private record TieLinePeaks(double orientation, double spacing, List<Double> densityPositions,
                                List<Double> densityValues,
                                List<Double> peakPositions) {

        private static TieLinePeaks empty() {
            return new TieLinePeaks(Double.NaN, Double.NaN, List.of(), List.of(), List.of());
        }
    }
}
