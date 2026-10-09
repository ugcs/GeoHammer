package com.ugcs.geohammer.service.magnetics;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.util.Check;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LineLevelingService {

    public List<@Nullable Number> level(List<GeoData> data, String series) {
        Check.notEmpty(data);
        Check.notEmpty(series);

        Map<Integer, List<Double>> valuesByLine = new LinkedHashMap<>();
        List<Double> allValues = new ArrayList<>();
        for (GeoData value : data) {
            Number field = value.getNumber(series);
            if (field == null || !Double.isFinite(field.doubleValue())) {
                continue;
            }
            double numericValue = field.doubleValue();
            valuesByLine.computeIfAbsent(value.getLineOrDefault(0), ignored -> new ArrayList<>()).add(numericValue);
            allValues.add(numericValue);
        }
        Check.condition(valuesByLine.size() > 1, "Line leveling requires at least two survey lines");

        double referenceMedian = median(allValues);
        Map<Integer, Double> offsetsByLine = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<Double>> entry : valuesByLine.entrySet()) {
            offsetsByLine.put(entry.getKey(), referenceMedian - median(entry.getValue()));
        }

        List<@Nullable Number> leveled = new ArrayList<>(data.size());
        for (GeoData value : data) {
            Number field = value.getNumber(series);
            if (field == null || !Double.isFinite(field.doubleValue())) {
                leveled.add(null);
                continue;
            }
            leveled.add(field.doubleValue() + offsetsByLine.get(value.getLineOrDefault(0)));
        }
        return leveled;
    }

    private static double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compare);
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 0
                ? (sorted.get(middle - 1) + sorted.get(middle)) / 2
                : sorted.get(middle);
    }
}
