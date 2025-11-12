package com.ugcs.geohammer.model;

import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Strings;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

// columns are sorted in order of additions
public class ColumnSchema implements Iterable<Column> {

    // header -> column
    private final Map<String, Column> columns = new LinkedHashMap<>();

    // index: header -> column index
    private Map<String, Integer> columnIndices;

    // index: semantic -> header
    private Map<String, String> headersBySemantic;

    public static ColumnSchema copy(ColumnSchema schema) {
        if (schema == null) {
            return null;
        }
        ColumnSchema copy = new ColumnSchema();
        schema.columns.forEach((header, column) -> {
            copy.columns.put(header, Column.copy(column));
        });
        return copy;
    }

    private void invalidateIndex() {
        columnIndices = null;
        headersBySemantic = null;
    }

    public int getColumnIndex(String header) {
        if (columnIndices == null) {
            // rebuild index
            columnIndices = new HashMap<>(columns.size());
            int columnIndex = 0;
            for (Column column : columns.values()) {
                columnIndices.put(column.getHeader(), columnIndex++);
            }
        }
        return columnIndices.getOrDefault(header, -1);
    }

    public String getHeaderBySemantic(String semantic) {
        if (headersBySemantic == null) {
            // rebuild index
            headersBySemantic = new HashMap<>();
            for (Column column : columns.values()) {
                if (!Strings.isNullOrEmpty(column.getSemantic()))
                    headersBySemantic.put(column.getSemantic(), column.getHeader());
            }
        }
        return headersBySemantic.get(semantic);
    }

    public int numColumns() {
        return columns.size();
    }

    public int numDisplayColumns() {
        int num = 0;
        for (Column column : columns.values()) {
            if (column.isDisplay()) {
                num++;
            }
        }
        return num;
    }

    @Override
    public @NotNull Iterator<Column> iterator() {
        return columns.values().iterator();
    }

    public Column getColumn(String header) {
        return columns.get(header);
    }

    public void addColumn(Column column) {
        Check.notNull(column);

        String header = column.getHeader();
        Check.notEmpty(header);
        Check.condition(!columns.containsKey(header),
                "Duplicate header: " + header);

        columns.put(header, column);
        invalidateIndex();
    }

    public Column removeColumn(String header) {
        Column removed = columns.remove(header);
        invalidateIndex();

        return removed;
    }

    // helpers

    public String getColumnSemantic(String header) {
        Column column = getColumn(header);
        return column != null ? column.getSemantic() : null;
    }

    public String getColumnUnit(String header) {
        Column column = getColumn(header);
        return column != null ? column.getUnit() : Strings.empty();
    }

    public boolean isColumnDisplay(String header) {
        Column column = getColumn(header);
        return column != null && column.isDisplay();
    }

    public Set<String> getDisplayHeaders() {
        Set<String> displayHeaders = new LinkedHashSet<>(numDisplayColumns());
        for (Column column : columns.values()) {
            if (!column.isDisplay()) {
                continue;
            }
            displayHeaders.add(column.getHeader());
        }
        return displayHeaders;
    }
}
