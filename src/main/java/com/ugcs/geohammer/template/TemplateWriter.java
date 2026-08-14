package com.ugcs.geohammer.template;

import com.ugcs.geohammer.model.template.DataMapping;
import com.ugcs.geohammer.model.template.FileFormat;
import com.ugcs.geohammer.model.template.SkipLinesTo;
import com.ugcs.geohammer.model.template.Template;
import com.ugcs.geohammer.model.template.data.BaseData;
import com.ugcs.geohammer.model.template.data.Date;
import com.ugcs.geohammer.model.template.data.DateTime;
import com.ugcs.geohammer.model.template.data.SensorData;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Strings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class TemplateWriter {

    private static final String INDENT = "  ";

    private TemplateWriter() {
    }

    public static void write(String yaml, Path path) throws IOException {
        Check.notNull(yaml);
        Check.notNull(path);

        Files.writeString(path, yaml);
    }

    public static String toYaml(Template template) {
        Check.notNull(template);

        StringBuilder yaml = new StringBuilder();
        append(yaml, 0, "name", quote(template.getName()));
        append(yaml, 0, "file-type", template.getFileType().name());
        append(yaml, 0, "match-regex", quote(template.getMatchRegex()));
        append(yaml, 0, "positional", template.isPositional());
        append(yaml, 0, "gps-time", template.isGpsTime());
        append(yaml, 0, "reorder-by-time", template.isReorderByTime());
        if (template.isReadOnly()) {
            append(yaml, 0, "read-only", true);
        }
        appendFileFormat(yaml, template.getFileFormat());
        appendSkipLinesTo(yaml, template.getSkipLinesTo());
        appendDataMapping(yaml, template.getDataMapping());
        return yaml.toString();
    }

    private static void appendFileFormat(StringBuilder yaml, FileFormat format) {
        if (format == null) {
            return;
        }
        append(yaml, 0, "file-format", null);
        append(yaml, 1, "has-header", format.isHasHeader());
        append(yaml, 1, "comment-prefix", quote(format.getCommentPrefix()));
        append(yaml, 1, "decimal-separator", quote(format.getDecimalSeparator()));
        if (!Strings.isNullOrEmpty(format.getSeparator())) {
            append(yaml, 1, "separator", quote(format.getSeparator()));
        }
        List<String> separators = format.getSeparators();
        if (!Nulls.isNullOrEmpty(separators)) {
            append(yaml, 1, "separators", null);
            for (String separator : separators) {
                append(yaml, 2, "- " + quote(separator));
            }
        }
        append(yaml, 1, "repeatable-separator", format.isRepeatableSeparator());
    }

    private static void appendSkipLinesTo(StringBuilder yaml, SkipLinesTo skipLinesTo) {
        if (skipLinesTo == null) {
            return;
        }
        append(yaml, 0, "skip-lines-to", null);
        append(yaml, 1, "match-regex", quote(skipLinesTo.getMatchRegex()));
        append(yaml, 1, "skip-matched-line", skipLinesTo.isSkipMatchedLine());
    }

    private static void appendDataMapping(StringBuilder yaml, DataMapping mapping) {
        if (mapping == null) {
            return;
        }
        append(yaml, 0, "data-mapping", null);
        appendColumn(yaml, "latitude", mapping.getLatitude());
        appendColumn(yaml, "longitude", mapping.getLongitude());
        appendDate(yaml, mapping.getDate());
        appendDateTime(yaml, "time", mapping.getTime());
        appendDateTime(yaml, "date-time", mapping.getDateTime());
        appendColumn(yaml, "timestamp", mapping.getTimestamp());
        appendDataValues(yaml, mapping.getDataValues());
    }

    private static void appendColumn(StringBuilder yaml, String key, BaseData column) {
        if (column == null) {
            return;
        }
        append(yaml, 1, key, null);
        appendColumnRef(yaml, column);
    }

    private static void appendColumnRef(StringBuilder yaml, BaseData column) {
        if (!Strings.isNullOrBlank(column.getHeader())) {
            append(yaml, 2, "header", quote(column.getHeader()));
        }
        if (column.getIndex() != null) {
            append(yaml, 2, "index", column.getIndex());
        }
        if (!Strings.isNullOrBlank(column.getRegex())) {
            append(yaml, 2, "regex", quote(column.getRegex()));
        }
    }

    private static void appendDate(StringBuilder yaml, Date date) {
        if (date == null) {
            return;
        }
        append(yaml, 1, "date", null);
        if (date.getSource() == Date.Source.FileName) {
            append(yaml, 2, "source", Date.Source.FileName.name());
        }
        appendColumnRef(yaml, date);
        appendFormats(yaml, date);
    }

    private static void appendDateTime(StringBuilder yaml, String key, DateTime dateTime) {
        if (dateTime == null) {
            return;
        }
        append(yaml, 1, key, null);
        appendColumnRef(yaml, dateTime);
        appendFormats(yaml, dateTime);
    }

    private static void appendFormats(StringBuilder yaml, DateTime dateTime) {
        append(yaml, 2, "format", quote(dateTime.getFormat()));

        List<String> formats = dateTime.getFormats();
        if (Nulls.isNullOrEmpty(formats)) {
            return;
        }
        append(yaml, 2, "formats", null);
        for (String format : formats) {
            append(yaml, 3, "- " + quote(format));
        }
    }

    private static void appendDataValues(StringBuilder yaml, List<SensorData> dataValues) {
        if (Nulls.isNullOrEmpty(dataValues)) {
            return;
        }
        append(yaml, 1, "data-values", null);
        for (SensorData dataValue : dataValues) {
            if (!Strings.isNullOrBlank(dataValue.getHeader())) {
                append(yaml, 2, "- header: " + quote(dataValue.getHeader()));
                if (dataValue.getIndex() != null) {
                    append(yaml, 3, "index", dataValue.getIndex());
                }
            } else {
                append(yaml, 2, "- index: " + dataValue.getIndex());
            }
            append(yaml, 3, "semantic", quote(dataValue.getSemantic()));
            if (!Strings.isNullOrBlank(dataValue.getUnits())) {
                append(yaml, 3, "units", quote(dataValue.getUnits()));
            }
            if (!Strings.isNullOrBlank(dataValue.getRegex())) {
                append(yaml, 3, "regex", quote(dataValue.getRegex()));
            }
            if (dataValue.getDecimals() != BaseData.DEFAULT_DECIMALS) {
                append(yaml, 3, "decimals", dataValue.getDecimals());
            }
            if (dataValue.isReadOnly()) {
                append(yaml, 3, "read-only", true);
            }
        }
    }

    private static void append(StringBuilder yaml, int level, String key, Object value) {
        append(yaml, level, value != null ? key + ": " + value : key + ":");
    }

    private static void append(StringBuilder yaml, int level, String text) {
        yaml.append(INDENT.repeat(level)).append(text).append("\n");
    }

    private static String quote(String value) {
        return "'" + Strings.nullToEmpty(value).replace("'", "''") + "'";
    }
}
