package com.ugcs.geohammer.format.csv.parser;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.model.Column;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.template.DataMapping;
import com.ugcs.geohammer.model.template.Template;
import com.ugcs.geohammer.model.template.data.BaseData;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Strings;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FixedWidthParser extends Parser {

    public FixedWidthParser(Template template) {
        super(template);
    }

    public static BaseData[] getFixedWidthColumns(Template template) {
        DataMapping mapping = template.getDataMapping();
        List<Short> widths = Nulls.toEmpty(template.getFileFormat().getColumnLengths());

        List<BaseData> templateColumns = new ArrayList<>();
        templateColumns.addAll(mapping.getMetaValues());
        templateColumns.addAll(mapping.getDataValues());

        BaseData[] columns = new BaseData[widths.size()];
        for (BaseData column : templateColumns) {
            Integer index = column.getIndex();
            if (index == null || index < 0 || index >= columns.length) {
                continue;
            }
            columns[index] = column;
        }
        return columns;
    }

    public static String[] getFixedWidthHeaders(Template template) {
        BaseData[] columns = getFixedWidthColumns(template);
        String[] headers = new String[columns.length];
        for (int i = 0; i < columns.length; i++) {
            headers[i] = columns[i] != null
                    ? Strings.emptyToNull(Strings.trim(columns[i].getHeader()))
                    : "Column " + i;
        }
        return headers;
    }

    public String[] splitLine(String line) {
        List<Short> widths = Nulls.toEmpty(template.getFileFormat().getColumnLengths());
        String[] tokens = new String[widths.size()];
        int offset = 0;
        for (int i = 0; i < tokens.length; i++) {
            int width = widths.get(i);
            String token = Strings.empty();
            if (offset < line.length()) {
                token = line.substring(offset, Math.min(offset + width, line.length())).trim();
            }
            tokens[i] = token;
            offset += width;
        }
        return tokens;
    }

    @Override
    protected String[] readHeaders(BufferedReader r) {
        return getFixedWidthHeaders(template);
    }

    @Override
    protected String[] readValues(BufferedReader r) throws IOException {
        String line;
        while ((line = r.readLine()) != null) {
            if (isBlankOrCommented(line)) {
                continue;
            }
            return splitLine(line);
        }
        return null;
    }

    @Override
    protected void onParsed(ColumnSchema columns, List<GeoData> values) {
        // mark all columns as read-only
        for (Column column : columns) {
            column.setReadOnly(true);
        }
    }
}
