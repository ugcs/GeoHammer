package com.ugcs.geohammer.service.magnetics;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.util.Check;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class RtpDirectionService {

    private final IgrfModel igrfModel;

    public RtpDirectionService(IgrfModel igrfModel) {
        this.igrfModel = igrfModel;
    }

    public RtpDirection derive(List<GeoData> data, @Nullable Instant fallbackTimestamp) {
        Check.notEmpty(data);

        double latitudeSum = 0;
        double longitudeSum = 0;
        double altitudeSum = 0;
        int count = 0;
        long earliest = Long.MAX_VALUE;
        long latest = Long.MIN_VALUE;
        for (GeoData value : data) {
            Double latitude = value.getLatitude();
            Double longitude = value.getLongitude();
            if (latitude == null || longitude == null) {
                continue;
            }
            latitudeSum += latitude;
            longitudeSum += longitude;
            altitudeSum += value.getAltitude() != null ? value.getAltitude() : 0;
            count++;
            Long timestamp = value.getTimestamp();
            if (timestamp != null) {
                earliest = Math.min(earliest, timestamp);
                latest = Math.max(latest, timestamp);
            }
        }
        Check.condition(count > 0, "RTP requires survey samples with latitude and longitude");
        Instant timestamp = earliest != Long.MAX_VALUE
                ? Instant.ofEpochMilli(earliest + (latest - earliest) / 2)
                : fallbackTimestamp;
        Check.condition(timestamp != null, "RTP requires sample timestamps or a fallback date");

        IgrfField field = igrfModel.evaluate(timestamp, latitudeSum / count, longitudeSum / count, altitudeSum / count);
        double horizontal = Math.hypot(field.north(), field.east());
        double inclination = Math.toDegrees(Math.atan2(field.down(), horizontal));
        double declination = Math.toDegrees(Math.atan2(field.east(), field.north()));
        return new RtpDirection(inclination, declination, timestamp);
    }
}
