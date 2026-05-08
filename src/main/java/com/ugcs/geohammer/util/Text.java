package com.ugcs.geohammer.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ugcs.geohammer.format.csv.parser.IncorrectFormatException;

public final class Text {

    public static final String GPST_FORMAT = "GPST";

	private static final Pattern FRACTION_TAIL = Pattern.compile("\\.f+$");

	private static final ThreadLocal<DecimalFormat> numberFormat
            = ThreadLocal.withInitial(Text::defaultNumberFormat);

	private static final Map<String, DateTimeFormatter> formattersByPattern = new ConcurrentHashMap<>();

	private Text() {
    }

    public static DecimalFormat defaultNumberFormat() {
        DecimalFormat format = new DecimalFormat(
                "0.#################",
                DecimalFormatSymbols.getInstance(Locale.US));
        format.setGroupingUsed(false);
        return format;
    }

    public static DecimalFormat createNumberFormat(int minFractionDigits, int maxFractionDigits) {
        DecimalFormat format = new DecimalFormat(
                "0.#################",
                DecimalFormatSymbols.getInstance(Locale.US));
        format.setGroupingUsed(false);
        format.setMinimumFractionDigits(minFractionDigits);
        format.setMaximumFractionDigits(maxFractionDigits);
        return format;
    }

    public static String formatNumber(Number number) {
        if (number == null) {
            return Strings.empty();
        }
        return numberFormat.get().format(number);
    }

    public static String formatNumber(Number number, int numFractionDigits) {
        if (number == null) {
            return Strings.empty();
        }
        DecimalFormat format = createNumberFormat(numFractionDigits, numFractionDigits);
        return format.format(number);
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
        Check.notNull(format, "Date format is not specified");
        try {
            return LocalDate.parse(value, formatterFor(format));
        } catch (DateTimeParseException e) {
            throw new IncorrectFormatException(value, format);
        }
    }

    public static LocalTime parseTime(String value, String format) {
        if (Strings.isNullOrBlank(value)) {
            return null;
        }
        Check.notNull(format, "Time format is not specified");
        try {
            return LocalTime.parse(value, formatterFor(format));
        } catch (DateTimeParseException e) {
            throw new IncorrectFormatException(value, format);
        }
    }

    public static LocalDateTime parseDateTime(String value, String format) {
        if (Strings.isNullOrBlank(value)) {
            return null;
        }
        Check.notNull(format, "DateTime format is not specified");
        if (format.equals(GPST_FORMAT)) {
            return parseGpsDateTime(value);
        }
        try {
            return LocalDateTime.parse(value, formatterFor(format));
        } catch (DateTimeParseException e) {
            throw new IncorrectFormatException(value, format);
        }
    }

    private static DateTimeFormatter formatterFor(String pattern) {
        return formattersByPattern.computeIfAbsent(pattern, Text::buildDateTimeFormatter);
    }

	private static DateTimeFormatter buildDateTimeFormatter(String pattern) {
		Matcher matcher = FRACTION_TAIL.matcher(pattern);
		if (!matcher.find()) {
			return DateTimeFormatter.ofPattern(pattern, Locale.US);
		}
		return new DateTimeFormatterBuilder()
				.appendPattern(pattern.substring(0, matcher.start()))
				.appendLiteral('.')
				.appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, false)
				.toFormatter(Locale.US);
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

    public static String unescape(String escaped) {
        if (escaped == null) {
            return null;
        }
        int n = escaped.length();
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            char c0 = escaped.charAt(i);
            if (c0 == '\\' && i + 1 < n) {
                char c1 = escaped.charAt(++i);
                switch (c1) {
                    case 't' -> sb.append('\t');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case '\\' -> sb.append('\\');
                    default -> sb.append(c0).append(c1);
                }
            } else {
                sb.append(c0);
            }
        }
        return sb.toString();
    }
}
