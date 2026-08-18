package com.ugcs.geohammer.template;

import com.ugcs.geohammer.template.model.ParsedSample;
import com.ugcs.geohammer.util.Check;

public final class ColumnMatchers {

    public static final ColumnMatcher LATITUDE = new HeaderContains("lat");

    public static final ColumnMatcher LONGITUDE = new HeaderContains("lon", "lng");

    public static final ColumnMatcher ALTITUDE = new HeaderContains("alt", "height", "elev");

    public static final ColumnMatcher DATE_TIME = new HeaderContains("date", "time", "gpst");

    public static final ColumnMatcher DATE = new HeaderContains("date", "day");

    public static final ColumnMatcher TIME = new HeaderContains("time");

    public static final ColumnMatcher TIMESTAMP = new HeaderContains("time", "ts", "unix", "posix", "millis", "epoch");

    public static final ColumnMatcher LINE = new HeaderContains("line", "next wp");

    public static String match(ParsedSample parsedSample, ColumnMatcher matcher) {
        Check.notNull(parsedSample);
        Check.notNull(matcher);

        for (String header : parsedSample.headers()) {
            if (matcher.matches(header)) {
                return header;
            }
        }
        return null;
    }
}
