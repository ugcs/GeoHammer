package com.ugcs.geohammer.template.model;

import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Strings;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record ParsedSample(List<String> headers, List<List<String>> rows, int numSkipLines) {

    public ParsedSample {
        Check.notNull(headers);
        Check.notNull(rows);
    }

    public static ParsedSample empty() {
        return new ParsedSample(List.of(), List.of(), 0);
    }

    public boolean isEmpty() {
        return headers.isEmpty();
    }

    public int numRows() {
        return rows.size();
    }

    public int numColumns() {
        return headers.size();
    }

    public int columnIndex(@Nullable String header) {
        return header != null ? headers.indexOf(header) : -1;
    }

    public List<String> column(int columnIndex) {
        if (columnIndex < 0 || columnIndex >= numColumns()) {
            return List.of();
        }
        List<String> values = new ArrayList<>(numRows());
        for (List<String> row : rows) {
            // rows may have fewer values than the header row
            String value = columnIndex < row.size()
                    ? Strings.nullToEmpty(row.get(columnIndex))
                    : Strings.empty();
            values.add(value);
        }
        return values;
    }

    public List<String> column(@Nullable String header) {
        return column(columnIndex(header));
    }
}
