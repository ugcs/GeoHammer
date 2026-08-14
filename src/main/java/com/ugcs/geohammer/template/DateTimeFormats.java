package com.ugcs.geohammer.template;

import com.ugcs.geohammer.template.model.ColumnType;
import com.ugcs.geohammer.template.model.Defaults;
import com.ugcs.geohammer.util.Text;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class DateTimeFormats {

    private DateTimeFormats() {
    }

    public static List<String> knownFormats(ColumnType type) {
        return switch (type) {
            case DATE -> Defaults.DATE_FORMATS;
            case TIME -> Defaults.TIME_FORMATS;
            case DATE_TIME -> Defaults.DATE_TIME_FORMATS;
            default -> List.of();
        };
    }

    // returns known formats covering all the values,
    // or nothing if some value is left unmatched
    public static List<String> matchFormats(List<String> values, ColumnType type) {
        if (values.isEmpty()) {
            return List.of();
        }
        List<String> unmatched = new ArrayList<>(values);
        List<String> matched = new ArrayList<>(1);
        for (String format : knownFormats(type)) {
            if (unmatched.isEmpty()) {
                break;
            }
            if (unmatched.removeIf(value -> matches(value, format, type))) {
                matched.add(format);
            }
        }
        return unmatched.isEmpty() ? matched : List.of();
    }

    public static @Nullable Object parseFirst(String value, List<String> formats, ColumnType type) {
        try {
            return parse(value, formats, type);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean matches(String value, String format, ColumnType type) {
        try {
            return parse(value, List.of(format), type) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static @Nullable Object parse(String value, List<String> formats, ColumnType type) {
        return switch (type) {
            case DATE -> Text.parseDate(value, formats);
            case TIME -> Text.parseTime(value, formats);
            case DATE_TIME -> Text.parseDateTime(value, formats);
            default -> null;
        };
    }
}
