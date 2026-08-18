package com.ugcs.geohammer.template;

import com.ugcs.geohammer.model.Semantic;
import com.ugcs.geohammer.model.template.DataMapping;
import com.ugcs.geohammer.model.template.FileFormat;
import com.ugcs.geohammer.model.template.SkipLinesTo;
import com.ugcs.geohammer.model.template.Template;
import com.ugcs.geohammer.model.template.data.BaseData;
import com.ugcs.geohammer.model.template.data.Date;
import com.ugcs.geohammer.model.template.data.DateTime;
import com.ugcs.geohammer.model.template.data.Latitude;
import com.ugcs.geohammer.model.template.data.Longitude;
import com.ugcs.geohammer.model.template.data.SensorData;
import com.ugcs.geohammer.template.model.ColumnModel;
import com.ugcs.geohammer.template.model.ColumnType;
import com.ugcs.geohammer.template.model.ColumnsModel;
import com.ugcs.geohammer.template.model.Defaults;
import com.ugcs.geohammer.template.model.FormatModel;
import com.ugcs.geohammer.template.model.SkipLinesModel;
import com.ugcs.geohammer.template.model.TemplateModel;
import com.ugcs.geohammer.template.model.TimeReference;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Strings;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class TemplateFactory {

    private static final String DECIMAL_SEPARATOR = ".";

    private static final String ALTITUDE_UNITS = "m";

    private TemplateFactory() {
    }

    // template is not initialized here to keep it identical
    // to the one loaded from a stored file
    public static Template create(TemplateModel model) {
        Check.notNull(model);

        Template template = new Template();
        template.setName(Strings.trim(model.getName()));
        template.setFileType(Template.FileType.CSV);
        template.setMatchRegex(Strings.trim(model.getMatchRegex()));
        template.setFileFormat(createFileFormat(model.getFormat()));
        template.setSkipLinesTo(createSkipLinesTo(model));
        template.setDataMapping(createDataMapping(model));
        template.setPositional(model.isPositional());
        template.setGpsTime(model.getTimeReference() == TimeReference.GPS);
        template.setReorderByTime(model.isReorderByTime());
        template.setReadOnly(model.isReadOnly());
        return template;
    }

    private static FileFormat createFileFormat(FormatModel options) {
        FileFormat format = new FileFormat();
        format.setHasHeader(options.isHasHeader());
        format.setCommentPrefix(Strings.nullToEmpty(options.getCommentPrefix()));
        format.setDecimalSeparator(DECIMAL_SEPARATOR);
        format.setRepeatableSeparator(options.isRepeatableSeparator());

        List<String> separators = new ArrayList<>(options.getSeparators());
        if (separators.size() == 1) {
            format.setSeparator(separators.getFirst());
        } else if (!separators.isEmpty()) {
            format.setSeparators(separators);
        }
        return format;
    }

    private static @Nullable SkipLinesTo createSkipLinesTo(TemplateModel model) {
        SkipLinesModel options = model.getSkipLines();
        String regex = options.getMatchRegex();
        if (Strings.isNullOrBlank(regex)) {
            return null;
        }
        SkipLinesTo skipLinesTo = new SkipLinesTo();
        skipLinesTo.setMatchRegex(regex);
        skipLinesTo.setSkipMatchedLine(options.isSkipMatchedLine());
        return skipLinesTo;
    }

    private static DataMapping createDataMapping(TemplateModel model) {
        ColumnsModel columns = model.getColumns();
        DataMapping mapping = new DataMapping();

        Latitude latitude = new Latitude();
        applyColumn(latitude, model, columns.getColumn(ColumnType.LATITUDE));
        mapping.setLatitude(latitude);

        Longitude longitude = new Longitude();
        applyColumn(longitude, model, columns.getColumn(ColumnType.LONGITUDE));
        mapping.setLongitude(longitude);

        applyTime(mapping, model);
        mapping.setDataValues(createDataValues(model));

        return mapping;
    }

    // header-less files reference columns by index
    private static void applyColumn(BaseData data, TemplateModel model, @Nullable ColumnModel column) {
        if (column == null) {
            return;
        }
        if (model.getFormat().isHasHeader()) {
            data.setHeader(column.getHeader());
        } else {
            data.setIndex(getColumnIndex(model, column.getHeader()));
        }
        data.setRegex(Strings.emptyToNull((column.getRegex())));
    }

    private static @Nullable Integer getColumnIndex(TemplateModel model, @Nullable String column) {
        if (Strings.isNullOrBlank(column)) {
            return null;
        }
        int index = model.getParsedSample().columnIndex(column);
        if (index >= 0) {
            return index;
        }
        String name = column.startsWith(Defaults.INDEX_HEADER_PREFIX)
                ? column.substring(Defaults.INDEX_HEADER_PREFIX.length())
                : column;
        try {
            int parsed = Integer.parseInt(name.trim());
            return parsed >= 0 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void applyTime(DataMapping mapping, TemplateModel model) {
        ColumnsModel columns = model.getColumns();

        mapping.setDateTime(createDateTime(model, columns.getColumn(ColumnType.DATE_TIME)));
        mapping.setDate(createDate(model, columns.getColumn(ColumnType.DATE)));
        mapping.setTime(createDateTime(model, columns.getColumn(ColumnType.TIME)));

        ColumnModel timestampColumn = columns.getColumn(ColumnType.TIMESTAMP);
        if (timestampColumn != null) {
            BaseData timestamp = new BaseData();
            applyColumn(timestamp, model, timestampColumn);
            mapping.setTimestamp(timestamp);
        }
    }

    private static @Nullable Date createDate(TemplateModel model, @Nullable ColumnModel column) {
        if (column == null) {
            return null;
        }
        if (column.getDateSource() == ColumnModel.DateSource.FILE_NAME) {
            if (Strings.isNullOrBlank(column.getRegex())) {
                return null;
            }
            Date date = new Date();
            date.setSource(Date.Source.FileName);
            date.setRegex(Strings.emptyToNull(column.getRegex()));
            applyFormats(date, column.getFormats());
            return date;
        }
        if (Strings.isNullOrBlank(column.getHeader())) {
            return null;
        }
        Date date = new Date();
        applyColumn(date, model, column);
        applyFormats(date, column.getFormats());
        return date;
    }

    private static @Nullable DateTime createDateTime(TemplateModel model, @Nullable ColumnModel column) {
        if (column == null || Strings.isNullOrBlank(column.getHeader())) {
            return null;
        }
        DateTime dateTime = new DateTime();
        applyColumn(dateTime, model, column);
        applyFormats(dateTime, column.getFormats());
        return dateTime;
    }

    private static void applyFormats(DateTime dateTime, List<String> formats) {
        if (formats.isEmpty()) {
            return;
        }
        dateTime.setFormat(formats.getFirst());
        if (formats.size() > 1) {
            dateTime.setFormats(new ArrayList<>(formats.subList(1, formats.size())));
        }
    }

    private static List<SensorData> createDataValues(TemplateModel model) {
        List<SensorData> dataValues = new ArrayList<>();

        ColumnsModel columns = model.getColumns();

        ColumnModel altitude = columns.getColumn(ColumnType.ALTITUDE);
        if (altitude != null) {
            SensorData dataValue = new SensorData();
            applyColumn(dataValue, model, altitude);
            dataValue.setSemantic(Semantic.ALTITUDE.getName());
            dataValue.setUnits(ALTITUDE_UNITS);
            dataValue.setReadOnly(true);
            dataValues.add(dataValue);
        }

        ColumnModel line = columns.getColumn(ColumnType.LINE);
        if (line != null) {
            SensorData dataValue = new SensorData();
            applyColumn(dataValue, model, line);
            dataValue.setSemantic(Semantic.LINE.getName());
            dataValue.setReadOnly(true);
            dataValues.add(dataValue);
        }

        for (ColumnModel column : columns.getColumns()) {
            if (column.getType() != ColumnType.VALUE
                    || Strings.isNullOrBlank(column.getHeader())) {
                continue;
            }
            SensorData dataValue = new SensorData();
            applyColumn(dataValue, model, column);
            dataValue.setSemantic(column.getHeader());
            dataValue.setUnits(Strings.emptyToNull(Strings.trim(column.getUnits())));
            dataValue.setDecimals(column.getDecimals());
            dataValues.add(dataValue);
        }
        return dataValues;
    }

    public static String createMatchRegex(TemplateModel model) {
        if (!model.getFormat().isHasHeader()) {
            return Strings.empty();
        }
        StringBuilder regex = new StringBuilder("^.*");
        for (ColumnModel column : model.getColumns().getColumns()) {
            if (column.getType() != ColumnType.NONE
                    && !Strings.isNullOrBlank(column.getHeader())) {
                regex.append(Pattern.quote(column.getHeader())).append(".*");
            }
        }
        return regex.toString();
    }
}
