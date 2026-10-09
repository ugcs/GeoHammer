package com.ugcs.geohammer.service.magnetics;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.util.Check;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class IgrfRemovalService {

    private final IgrfModel igrfModel;

    public IgrfRemovalService(IgrfModel igrfModel) {
        this.igrfModel = igrfModel;
    }

    public List<@Nullable Number> remove(List<GeoData> data, String inputSeries,
                                         @Nullable Instant fallbackTimestamp) {
        Check.notEmpty(data);
        Check.notEmpty(inputSeries);

        List<@Nullable Number> corrected = new ArrayList<>(data.size());
        for (GeoData value : data) {
            Number magneticField = value.getNumber(inputSeries);
            if (magneticField == null) {
                corrected.add(null);
                continue;
            }
            Double latitude = value.getLatitude();
            Double longitude = value.getLongitude();
            Check.condition(latitude != null && longitude != null,
                    "Magnetic samples must have latitude and longitude");
            Instant timestamp = value.getTimestamp() != null
                    ? Instant.ofEpochMilli(value.getTimestamp())
                    : fallbackTimestamp;
            Check.condition(timestamp != null,
                    "Magnetic samples must have timestamps or a fallback date");
            double altitude = value.getAltitude() != null ? value.getAltitude() : 0;
            IgrfField field = igrfModel.evaluate(timestamp, latitude, longitude, altitude);
            corrected.add(magneticField.doubleValue() - field.totalIntensity());
        }
        return corrected;
    }
}
