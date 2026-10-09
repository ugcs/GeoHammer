package com.ugcs.geohammer.service.magnetics;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.util.Check;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CrossoverLevelingService {

    public CrossoverLevelingResult level(List<GeoData> data, String series, Set<Integer> tieLines) {
        Check.notEmpty(data);
        Check.notEmpty(series);
        Check.notEmpty(tieLines);

        Map<Integer, List<Sample>> lines = collectLines(data, series);
        for (Integer tieLine : tieLines) {
            Check.condition(lines.containsKey(tieLine), "Tie line " + tieLine + " is not present in this survey");
        }
        List<Crossover> crossovers = findCrossovers(lines, tieLines);
        Check.condition(!crossovers.isEmpty(), "No survey/tie-line crossovers were found");

        Map<Integer, List<Double>> corrections = new LinkedHashMap<>();
        double sumSquares = 0;
        for (Crossover crossover : crossovers) {
            corrections.computeIfAbsent(crossover.surveyLine(), ignored -> new ArrayList<>()).add(-crossover.error());
            sumSquares += crossover.error() * crossover.error();
        }
        Map<Integer, Double> offsets = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<Double>> entry : corrections.entrySet()) {
            offsets.put(entry.getKey(), mean(entry.getValue()));
        }

        List<@Nullable Number> leveled = new ArrayList<>(data.size());
        for (GeoData value : data) {
            Number field = value.getNumber(series);
            if (field == null || !Double.isFinite(field.doubleValue())) {
                leveled.add(null);
                continue;
            }
            leveled.add(field.doubleValue() + offsets.getOrDefault(value.getLineOrDefault(0), 0.0));
        }
        return new CrossoverLevelingResult(leveled, crossovers, offsets,
                Math.sqrt(sumSquares / crossovers.size()));
    }

    private static Map<Integer, List<Sample>> collectLines(List<GeoData> data, String series) {
        Map<Integer, List<Sample>> lines = new LinkedHashMap<>();
        for (GeoData value : data) {
            LatLon location = value.getLatLon();
            Number field = value.getNumber(series);
            if (location == null || field == null || !Double.isFinite(field.doubleValue())) {
                continue;
            }
            lines.computeIfAbsent(value.getLineOrDefault(0), ignored -> new ArrayList<>())
                    .add(new Sample(location.getLatDgr(), location.getLonDgr(), field.doubleValue()));
        }
        lines.values().removeIf(line -> line.size() < 2);
        Check.condition(!lines.isEmpty(), "Survey has no lines with coordinates and magnetic values");
        return lines;
    }

    private static List<Crossover> findCrossovers(Map<Integer, List<Sample>> lines, Set<Integer> tieLines) {
        List<Crossover> crossovers = new ArrayList<>();
        for (Map.Entry<Integer, List<Sample>> survey : lines.entrySet()) {
            if (tieLines.contains(survey.getKey())) {
                continue;
            }
            for (Integer tieLine : tieLines) {
                List<Sample> tie = lines.get(tieLine);
                for (int i = 1; i < survey.getValue().size(); i++) {
                    for (int j = 1; j < tie.size(); j++) {
                        Crossover crossover = intersect(survey.getKey(), tieLine,
                                survey.getValue().get(i - 1), survey.getValue().get(i), tie.get(j - 1), tie.get(j));
                        if (crossover != null) {
                            crossovers.add(crossover);
                        }
                    }
                }
            }
        }
        return crossovers;
    }

    private static @Nullable Crossover intersect(int surveyLine, int tieLine, Sample a, Sample b, Sample c, Sample d) {
        double denominator = (b.longitude - a.longitude) * (d.latitude - c.latitude)
                - (b.latitude - a.latitude) * (d.longitude - c.longitude);
        if (Math.abs(denominator) < 1e-12) {
            return null;
        }
        double t = ((c.longitude - a.longitude) * (d.latitude - c.latitude)
                - (c.latitude - a.latitude) * (d.longitude - c.longitude)) / denominator;
        double u = ((c.longitude - a.longitude) * (b.latitude - a.latitude)
                - (c.latitude - a.latitude) * (b.longitude - a.longitude)) / denominator;
        if (t < 0 || t > 1 || u < 0 || u > 1) {
            return null;
        }
        double latitude = a.latitude + t * (b.latitude - a.latitude);
        double longitude = a.longitude + t * (b.longitude - a.longitude);
        double surveyField = a.field + t * (b.field - a.field);
        double tieField = c.field + u * (d.field - c.field);
        return new Crossover(surveyLine, tieLine, latitude, longitude, surveyField - tieField);
    }

    private static double mean(List<Double> values) {
        double sum = 0;
        for (Double value : values) {
            sum += value;
        }
        return sum / values.size();
    }

    private record Sample(double latitude, double longitude, double field) {
    }
}
