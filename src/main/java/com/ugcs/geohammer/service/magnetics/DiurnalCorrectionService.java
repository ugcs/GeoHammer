package com.ugcs.geohammer.service.magnetics;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.util.Check;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class DiurnalCorrectionService {

    public DiurnalCorrectionResult correct(List<GeoData> surveyData, String surveySeries,
                                            List<GeoData> baseData, String baseSeries,
                                            @Nullable Double referenceField) {
        Check.notEmpty(surveyData);
        Check.notEmpty(surveySeries);
        Check.notEmpty(baseData);
        Check.notEmpty(baseSeries);
        Check.condition(referenceField == null || Double.isFinite(referenceField),
                "Reference field must be finite");

        List<TimedValue> baseValues = collectBaseValues(baseData, baseSeries);
        baseValues.sort(Comparator.comparingLong(TimedValue::timestamp));
        checkUniqueTimestamps(baseValues);

        long[] surveyTimeRange = findSurveyTimeRange(surveyData, surveySeries);
        long firstBaseTimestamp = baseValues.getFirst().timestamp();
        long lastBaseTimestamp = baseValues.getLast().timestamp();
        Check.condition(surveyTimeRange[0] >= firstBaseTimestamp && surveyTimeRange[1] <= lastBaseTimestamp,
                "Survey timestamps are outside the base-station time range");

        double baseReference = referenceField != null ? referenceField : median(baseValues);
        List<@Nullable Number> corrected = new ArrayList<>(surveyData.size());
        for (GeoData surveyValue : surveyData) {
            Long timestamp = surveyValue.getTimestamp();
            Number field = surveyValue.getNumber(surveySeries);
            if (timestamp == null || field == null) {
                corrected.add(null);
                continue;
            }
            double baseField = interpolate(baseValues, timestamp);
            corrected.add(field.doubleValue() - (baseField - baseReference));
        }
        return new DiurnalCorrectionResult(corrected, baseReference);
    }

    private static List<TimedValue> collectBaseValues(List<GeoData> data, String series) {
        List<TimedValue> values = new ArrayList<>(data.size());
        for (GeoData value : data) {
            Number field = value.getNumber(series);
            if (field == null) {
                continue;
            }
            Long timestamp = value.getTimestamp();
            Check.condition(timestamp != null,
                    "Base-station samples with magnetic values must have timestamps");
            values.add(new TimedValue(timestamp, field.doubleValue()));
        }
        Check.condition(values.size() >= 2,
                "Base station must contain at least two timestamped magnetic samples");
        return values;
    }

    private static void checkUniqueTimestamps(List<TimedValue> values) {
        for (int i = 1; i < values.size(); i++) {
            Check.condition(values.get(i - 1).timestamp() != values.get(i).timestamp(),
                    "Base station timestamps must be unique");
        }
    }

    private static long[] findSurveyTimeRange(List<GeoData> surveyData, String series) {
        long first = Long.MAX_VALUE;
        long last = Long.MIN_VALUE;
        for (GeoData value : surveyData) {
            if (value.getNumber(series) == null) {
                continue;
            }
            Long timestamp = value.getTimestamp();
            Check.condition(timestamp != null,
                    "Survey samples with magnetic values must have timestamps");
            first = Math.min(first, timestamp);
            last = Math.max(last, timestamp);
        }
        Check.condition(first != Long.MAX_VALUE, "Survey has no magnetic samples");
        return new long[]{first, last};
    }

    private static double median(List<TimedValue> values) {
        List<Double> fields = new ArrayList<>(values.size());
        for (TimedValue value : values) {
            fields.add(value.value());
        }
        fields.sort(Double::compare);
        int middle = fields.size() / 2;
        return fields.size() % 2 == 0
                ? (fields.get(middle - 1) + fields.get(middle)) / 2
                : fields.get(middle);
    }

    private static double interpolate(List<TimedValue> values, long timestamp) {
        int low = 0;
        int high = values.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            TimedValue value = values.get(middle);
            if (value.timestamp() == timestamp) {
                return value.value();
            }
            if (value.timestamp() < timestamp) {
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }

        TimedValue before = values.get(high);
        TimedValue after = values.get(low);
        double fraction = (double) (timestamp - before.timestamp()) / (after.timestamp() - before.timestamp());
        return before.value() + fraction * (after.value() - before.value());
    }

    private record TimedValue(long timestamp, double value) {
    }
}
