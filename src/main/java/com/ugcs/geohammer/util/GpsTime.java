package com.ugcs.geohammer.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public final class GpsTime {

    private static final NavigableMap<Long, Integer> LEAP_SECONDS = buildLeapSeconds();

    private GpsTime() {
    }

    // gps-time -> leap seconds
    public static NavigableMap<Long, Integer> buildLeapSeconds() {
        NavigableMap<Long, Integer> leapSeconds = new TreeMap<>();

        leapSeconds.put(toEpochMilli("1980-01-06"), 0);
        leapSeconds.put(toEpochMilli("1981-07-01"), 1);
        leapSeconds.put(toEpochMilli("1982-07-01"), 2);
        leapSeconds.put(toEpochMilli("1983-07-01"), 3);
        leapSeconds.put(toEpochMilli("1985-07-01"), 4);
        leapSeconds.put(toEpochMilli("1988-01-01"), 5);
        leapSeconds.put(toEpochMilli("1990-01-01"), 6);
        leapSeconds.put(toEpochMilli("1991-01-01"), 7);
        leapSeconds.put(toEpochMilli("1992-07-01"), 8);
        leapSeconds.put(toEpochMilli("1993-07-01"), 9);
        leapSeconds.put(toEpochMilli("1994-07-01"), 10);
        leapSeconds.put(toEpochMilli("1996-01-01"), 11);
        leapSeconds.put(toEpochMilli("1997-07-01"), 12);
        leapSeconds.put(toEpochMilli("1999-01-01"), 13);
        leapSeconds.put(toEpochMilli("2006-01-01"), 14);
        leapSeconds.put(toEpochMilli("2009-01-01"), 15);
        leapSeconds.put(toEpochMilli("2012-07-01"), 16);
        leapSeconds.put(toEpochMilli("2015-07-01"), 17);
        leapSeconds.put(toEpochMilli("2017-01-01"), 18);

        return leapSeconds;
    }

    private static long toEpochMilli(String date) {
        return LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    public static int getLeapSeconds(LocalDateTime time) {
        long t = time.toInstant(ZoneOffset.UTC).toEpochMilli();
        Map.Entry<Long, Integer> entry = LEAP_SECONDS.floorEntry(t);
        return entry != null ? entry.getValue() : 0;
    }

    public static LocalDateTime gpsToUtc(LocalDateTime gpsTime) {
        int leapSeconds = getLeapSeconds(gpsTime); // approximate utc key by gps time
        LocalDateTime utc = gpsTime.minusSeconds(leapSeconds);
        leapSeconds = getLeapSeconds(utc); // update
        return gpsTime.minusSeconds(leapSeconds);
    }
}
