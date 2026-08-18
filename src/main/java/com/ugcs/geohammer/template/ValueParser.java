package com.ugcs.geohammer.template;

import com.ugcs.geohammer.template.model.ColumnModel;
import com.ugcs.geohammer.template.model.ColumnType;
import com.ugcs.geohammer.template.model.TemplateModel;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.util.Text;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
public class ValueParser {

    private final TemplateModel templateModel;

    public ValueParser(TemplateModel templateModel) {
        this.templateModel = templateModel;
    }

    public Preview parse(@Nullable String header, @Nullable String value) {
        if (Strings.isNullOrBlank(header) || Strings.isNullOrBlank(value)) {
            return Preview.plain(value);
        }
        try {
            ColumnModel column = templateModel.getColumns().getColumn(header);
            if (column == null) {
                return Preview.plain(value);
            }
            return switch (column.getType()) {
                case LATITUDE, LONGITUDE, ALTITUDE, LINE, VALUE -> number(value, column);
                case DATE_TIME, DATE, TIME -> dateTime(value, column);
                case TIMESTAMP -> epoch(value, column);
                case NONE -> Preview.plain(value);
            };
        } catch (RuntimeException e) {
            return Preview.error(value);
        }
    }

    private static Preview number(String value, ColumnModel column) {
        String extracted = extract(value, column.getRegex());
        if (Strings.isNullOrBlank(extracted)) {
            return Preview.error(value);
        }
        Number number = extracted.indexOf('.') >= 0
                ? (Number)Text.parseDouble(extracted)
                : (Number)Text.parseLong(extracted);
        if (number == null) {
            return Preview.error(value);
        }
        String text = number instanceof Double d && column.getType() == ColumnType.VALUE
                ? Text.formatNumber(d, column.getDecimals())
                : Text.formatNumber(number);
        return Preview.parsed(text);
    }

    private static Preview dateTime(String value, ColumnModel column) {
        String extracted = extract(value, column.getRegex());
        if (Strings.isNullOrBlank(extracted) || column.getFormats().isEmpty()) {
            return Preview.error(value);
        }
        Object parsed = DateTimeFormats.parseFirst(extracted, column.getFormats(), column.getType());
        return parsed != null ? Preview.parsed(parsed.toString()) : Preview.error(value);
    }

    private static Preview epoch(String value, ColumnModel column) {
        String extracted = extract(value, column.getRegex());
        if (Strings.isNullOrBlank(extracted)) {
            return Preview.error(value);
        }
        Long timestamp = Text.parseLong(extracted);
        if (timestamp == null) {
            return Preview.error(value);
        }
        LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp), ZoneOffset.UTC);
        return Preview.parsed(dateTime.toString());
    }

    private static @Nullable String extract(String value, @Nullable String regex) {
        return Text.matchPattern(value, Strings.emptyToNull(Strings.trim(regex)), false);
    }

    public enum Status {
        PLAIN,
        PARSED,
        ERROR
    }

    public record Preview(@Nullable String text, Status status) {

        public static Preview plain(@Nullable String value) {
            return new Preview(value, Status.PLAIN);
        }

        public static Preview parsed(String text) {
            return new Preview(text, Status.PARSED);
        }

        public static Preview error(String value) {
            return new Preview(value, Status.ERROR);
        }
    }
}
