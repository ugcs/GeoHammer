package com.ugcs.geohammer.service.magnetics;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.util.Check;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MicroLevelingService {

    public List<@Nullable Number> level(List<GeoData> data, String series, int windowSize) {
        Check.notEmpty(data);
        Check.notEmpty(series);
        Check.condition(windowSize >= 3 && windowSize % 2 == 1,
                "Micro-leveling window size must be an odd number of at least 3");

        Map<Integer, List<Sample>> samplesByLine = collectSamples(data, series);
        Check.condition(samplesByLine.size() >= 3, "Micro-leveling requires at least three survey lines");

        List<Line> lines = new ArrayList<>();
        for (Map.Entry<Integer, List<Sample>> entry : samplesByLine.entrySet()) {
            if (entry.getValue().size() >= 2) {
                lines.add(new Line(entry.getKey(), entry.getValue()));
            }
        }
        lines.sort(Comparator.comparingInt(Line::id));
        Check.condition(lines.size() >= 3, "Micro-leveling requires at least three survey lines with two samples");
        alignDirections(lines);

        double[] corrections = new double[data.size()];
        for (int lineIndex = 1; lineIndex < lines.size() - 1; lineIndex++) {
            applyCorrections(lines.get(lineIndex - 1), lines.get(lineIndex), lines.get(lineIndex + 1), windowSize,
                    corrections);
        }

        List<@Nullable Number> leveled = new ArrayList<>(data.size());
        for (int index = 0; index < data.size(); index++) {
            Number field = data.get(index).getNumber(series);
            leveled.add(field == null || !Double.isFinite(field.doubleValue())
                    ? null
                    : field.doubleValue() - corrections[index]);
        }
        return leveled;
    }

    private static Map<Integer, List<Sample>> collectSamples(List<GeoData> data, String series) {
        Map<Integer, List<Sample>> samplesByLine = new LinkedHashMap<>();
        for (int index = 0; index < data.size(); index++) {
            GeoData value = data.get(index);
            Number field = value.getNumber(series);
            if (field == null || !Double.isFinite(field.doubleValue())) {
                continue;
            }
            samplesByLine.computeIfAbsent(value.getLineOrDefault(0), ignored -> new ArrayList<>())
                    .add(new Sample(index, field.doubleValue(), value.getLatLon()));
        }
        return samplesByLine;
    }

    private static void alignDirections(List<Line> lines) {
        Line reference = null;
        for (Line line : lines) {
            if (line.hasDirection()) {
                reference = line;
                break;
            }
        }
        if (reference == null) {
            return;
        }
        for (Line line : lines) {
            if (line.hasDirection() && reference.dotDirection(line) < 0) {
                line.reverse();
            }
        }
    }

    private static void applyCorrections(Line previous, Line current, Line next, int windowSize, double[] corrections) {
        List<Double> residuals = new ArrayList<>(current.samples.size());
        for (int index = 0; index < current.samples.size(); index++) {
            double position = current.position(index);
            double neighborValue = (previous.interpolate(position) + next.interpolate(position)) / 2;
            residuals.add(current.samples.get(index).value - neighborValue);
        }
        int radius = windowSize / 2;
        for (int index = 0; index < current.samples.size(); index++) {
            int from = Math.max(0, index - radius);
            int to = Math.min(residuals.size(), index + radius + 1);
            corrections[current.samples.get(index).dataIndex] = median(residuals.subList(from, to));
        }
    }

    private static double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compare);
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 0
                ? (sorted.get(middle - 1) + sorted.get(middle)) / 2
                : sorted.get(middle);
    }

    private record Sample(int dataIndex, double value, @Nullable LatLon latLon) {
    }

    private static class Line {

        private final int id;

        private final List<Sample> samples;

        private Line(int id, List<Sample> samples) {
            this.id = id;
            this.samples = samples;
        }

        private int id() {
            return id;
        }

        private double position(int index) {
            return (double) index / (samples.size() - 1);
        }

        private double interpolate(double position) {
            double scaledIndex = position * (samples.size() - 1);
            int lowerIndex = (int) Math.floor(scaledIndex);
            int upperIndex = Math.min(lowerIndex + 1, samples.size() - 1);
            double fraction = scaledIndex - lowerIndex;
            return samples.get(lowerIndex).value * (1 - fraction) + samples.get(upperIndex).value * fraction;
        }

        private boolean hasDirection() {
            return samples.getFirst().latLon != null && samples.getLast().latLon != null;
        }

        private double dotDirection(Line other) {
            LatLon start = samples.getFirst().latLon;
            LatLon end = samples.getLast().latLon;
            LatLon otherStart = other.samples.getFirst().latLon;
            LatLon otherEnd = other.samples.getLast().latLon;
            return (end.getLatDgr() - start.getLatDgr()) * (otherEnd.getLatDgr() - otherStart.getLatDgr())
                    + (end.getLonDgr() - start.getLonDgr()) * (otherEnd.getLonDgr() - otherStart.getLonDgr());
        }

        private void reverse() {
            Collections.reverse(samples);
        }
    }
}
