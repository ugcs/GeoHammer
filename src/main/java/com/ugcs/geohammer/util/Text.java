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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Text {

    public static final String GPST_FORMAT = "GPST";

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
			throw new IncorrectFormatException(value, "number");
        }
    }

    public static Integer parseInt(String value) {
        if (Strings.isNullOrBlank(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
			throw new IncorrectFormatException(value, "number");
        }
    }

    public static Long parseLong(String value) {
        if (Strings.isNullOrBlank(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
			throw new IncorrectFormatException(value, "number");
        }
    }

    public static LocalDate parseDate(String value, List<String> formats) {
        if (Strings.isNullOrBlank(value)) {
            return null;
        }
        Check.hasNonEmptyElement(formats, "Date format is not specified");
        return parse(value, formats, (v, format) -> {
            try {
                return LocalDate.parse(value, formatterFor(format));
            } catch (DateTimeParseException e) {
                throw new IncorrectFormatException(value, format);
            }
        });
    }

    public static LocalTime parseTime(String value, List<String> formats) {
        if (Strings.isNullOrBlank(value)) {
            return null;
        }
        Check.hasNonEmptyElement(formats, "Time format is not specified");
        return parse(value, formats, (v, format) -> {
            try {
                return LocalTime.parse(value, formatterFor(format));
            } catch (DateTimeParseException e) {
                throw new IncorrectFormatException(value, format);
            }
        });
    }

    public static LocalDateTime parseDateTime(String value, List<String> formats) {
        if (Strings.isNullOrBlank(value)) {
            return null;
        }
        Check.hasNonEmptyElement(formats, "DateTime format is not specified");
        return parse(value, formats, (v, format) -> {
            if (format.equals(GPST_FORMAT)) {
                return parseGpsDateTime(value);
            }
            try {
                return LocalDateTime.parse(value, formatterFor(format));
            } catch (DateTimeParseException e) {
                throw new IncorrectFormatException(value, format);
            }
        });
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
            throw new IncorrectFormatException(value, GPST_FORMAT);
        }
    }

    public static <T> T parse(String value, List<String> formats, BiFunction<String, String, T> parser) {
        if (Strings.isNullOrBlank(value)) {
            return null;
        }

        RuntimeException firstError = null;
        for (String format : formats) {
            if (Strings.isNullOrEmpty(format)) {
                continue;
            }
            try {
                // null result means no match: try the next format
                T parsed = parser.apply(value, format);
                if (parsed != null) {
                    return parsed;
                }
            } catch (RuntimeException e) {
                if (firstError == null) {
                    firstError = e;
                }
            }
        }
        if (firstError != null) {
            throw firstError;
        }
        return null;
    }

    private static DateTimeFormatter formatterFor(String pattern) {
        return formattersByPattern.computeIfAbsent(pattern, Text::toFormatter);
    }

	private static DateTimeFormatter toFormatter(String pattern) {
		pattern = Strings.nullToEmpty(pattern).replace("f", "S");
		if (pattern.contains("S+")) {
			pattern = pattern.replace("S+", "");
			pattern = pattern.replace("S", "");
			return new DateTimeFormatterBuilder()
					.appendPattern(pattern)
					.appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, false)
					.toFormatter(Locale.US);
		}
		return DateTimeFormatter.ofPattern(pattern, Locale.US);
	}

    public static String escape(String raw) {
        if (raw == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '\t' -> sb.append("\\t");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\\' -> sb.append("\\\\");
                default -> sb.append(c);
            }
        }
        return sb.toString();
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
