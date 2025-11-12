package com.ugcs.geohammer.format.csv.parser;

import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Strings;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Text {

    private static final ThreadLocal<DecimalFormat> NUMBER_FORMAT
            = ThreadLocal.withInitial(Text::defaultNumberFormat);

    private Text() {
    }

    public static DecimalFormat defaultNumberFormat() {
        DecimalFormat format = new DecimalFormat(
                "0.#################",
                DecimalFormatSymbols.getInstance(Locale.US));
        format.setGroupingUsed(false);
        return format;
    }

    public static String formatNumber(Number number) {
        if (number == null) {
            return Strings.empty();
        }
        return NUMBER_FORMAT.get().format(number);
    }

    public static String matchPattern(String value, String regex) {
        return matchPattern(value, regex, true);
    }

    public static String matchPattern(String value, String regex, boolean fullMatch) {
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

    public static Double parseDouble(String value) {
        if (Strings.isNullOrBlank(value)) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Integer parseInt(String value) {
        if (Strings.isNullOrBlank(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Long parseLong(String value) {
        if (Strings.isNullOrBlank(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static LocalDate parseDate(String value, String format) {
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

    public static LocalTime parseTime(String value, String format) {
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

    public static LocalDateTime parseDateTime(String value, String format) {
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

    public static LocalDateTime parseGpsDateTime(String value) {
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
