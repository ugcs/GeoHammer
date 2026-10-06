package com.ugcs.geohammer.format.nmea;

import com.ugcs.geohammer.model.LatLon;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

public class NmeaFormatter {

    private static final long MICRO_MINUTES_PER_DEGREE = 60_000_000L;

    public @Nullable String replaceLocation(String nmea, LatLon latLon) {
        int start = nmea.indexOf('$');
        if (start < 0) {
            return null;
        }
        int end = nmea.indexOf('*', start);
        String tail;
        if (end >= 0) {
            tail = nmea.substring(Math.min(end + 3, nmea.length()));
        } else {
            end = nmea.length();
            while (end > start && Character.isWhitespace(nmea.charAt(end - 1))) {
                end--;
            }
            tail = nmea.substring(end);
        }

        String[] fields = nmea.substring(start + 1, end).split(",", -1);
        String sentenceId = fields[0];
        if (sentenceId.length() < 3) {
            return null;
        }
        int latitudeIndex = switch (sentenceId.substring(sentenceId.length() - 3)) {
            case "GGA" -> 2;
            case "RMC" -> 3;
            case "GLL" -> 1;
            default -> -1;
        };
        if (latitudeIndex < 0 || fields.length < latitudeIndex + 4) {
            return null;
        }

        double latitude = latLon.getLatDgr();
        double longitude = latLon.getLonDgr();
        fields[latitudeIndex] = formatAngle(Math.abs(latitude), 2);
        fields[latitudeIndex + 1] = latitude >= 0 ? "N" : "S";
        fields[latitudeIndex + 2] = formatAngle(Math.abs(longitude), 3);
        fields[latitudeIndex + 3] = longitude >= 0 ? "E" : "W";

        String body = String.join(",", fields);
        return "$" + body + "*" + computeChecksum(body) + tail;
    }

    private static String formatAngle(double degrees, int degreeDigits) {
        long microMinutes = Math.round(degrees * MICRO_MINUTES_PER_DEGREE);
        long wholeDegrees = microMinutes / MICRO_MINUTES_PER_DEGREE;
        long fraction = microMinutes % MICRO_MINUTES_PER_DEGREE;
        return String.format(Locale.ROOT, "%0" + degreeDigits + "d%02d.%06d",
                wholeDegrees, fraction / 1_000_000, fraction % 1_000_000);
    }

    private static String computeChecksum(String body) {
        int checksum = 0;
        for (int i = 0; i < body.length(); i++) {
            checksum ^= body.charAt(i);
        }
        return String.format(Locale.ROOT, "%02X", checksum);
    }
}
