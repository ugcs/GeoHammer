package com.ugcs.gprvisualizer.app.parsers;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ugcs.gprvisualizer.utils.Check;
import com.ugcs.gprvisualizer.utils.Strings;

public abstract class Parser {

    public abstract List<GeoData> parse(File file) throws IOException;

    public String matchPattern(String value, String regex) {
        return matchPattern(value, regex, true);
    }

    public String matchPattern(String value, String regex, boolean fullMatch) {
        if (Strings.isNullOrEmpty(value)) {
            return value;
        }
        if (!Strings.isNullOrEmpty(regex)) {
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(value);

            boolean matches = fullMatch ? matcher.matches() : matcher.find();
            return matches ? matcher.group() : null;
        }
        return value;
    }

    public Double parseDouble(String value) {
        if (Strings.isNullOrBlank(value)) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Integer parseInt(String value) {
        if (Strings.isNullOrBlank(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Long parseLong(String value) {
        if (Strings.isNullOrBlank(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public LocalDate parseDate(String value, String format) {
        if (Strings.isNullOrBlank(value)) {
            return null;
        }
        Check.notNull(format);
        try {
            return LocalDate.parse(value, DateTimeFormatter.ofPattern(format, Locale.US));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public LocalTime parseTime(String value, String format) {
        if (Strings.isNullOrBlank(value)) {
            return null;
        }
        Check.notNull(format);
        format = format.replaceAll("f", "S");
        try {
            return LocalTime.parse(value, DateTimeFormatter.ofPattern(format, Locale.US));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public LocalDateTime parseDateTime(String value, String format) {
        if (Strings.isNullOrBlank(value)) {
            return null;
        }
        Check.notNull(format);
        format = format.replaceAll("f", "S");
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern(format, Locale.US));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public LocalDateTime parseGpsDateTime(String value) {
        if (Strings.isNullOrBlank(value)) {
            return null;
        }
        String[] tokens = value.split(" ");
        if (tokens.length < 2) {
            return null;
        }
        try {
            int weeks = Integer.parseInt(tokens[0]);
            double seconds = Double.parseDouble(tokens[1]);
            LocalDateTime gpsEpoch = LocalDateTime.of(1980, 1, 6, 0, 0, 0);
            return gpsEpoch
                    .plusDays(weeks * 7L)
                    .plus((long) (seconds * 1000), ChronoUnit.MILLIS);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
