package com.ugcs.geohammer.format.csv.parser;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.util.Nulls;
import org.jspecify.annotations.Nullable;

import java.util.AbstractList;
import java.util.List;

public class ColumnView extends AbstractList<Number> {

    private final List<GeoData> values;

    private final String header;

    // column index resolved for a schema at a given version;
    // resolved again when a row has another schema or the layout changed
    private @Nullable ColumnIndex columnIndex;

    public ColumnView(List<GeoData> values, String header) {
        this.values = Nulls.toEmpty(values);
        this.header = header;
    }

    @Override
    public Number get(int index) {
        GeoData value = values.get(index);
        if (value == null) {
            return null;
        }
        ColumnIndex columnIndex = updateColumnIndex(value.getSchema());
        return value.getNumber(columnIndex.columnIndex());
    }

    private ColumnIndex updateColumnIndex(ColumnSchema schema) {
        ColumnIndex columnIndex = this.columnIndex;
        if (columnIndex == null
                || columnIndex.schema() != schema
                || columnIndex.schemaVersion() != schema.getVersion()) {
            columnIndex = new ColumnIndex(schema, schema.getVersion(), schema.getColumnIndex(header));
            this.columnIndex = columnIndex;
        }
        return columnIndex;
    }

    @Override
    public int size() {
        return values.size();
    }

    private record ColumnIndex(ColumnSchema schema, int schemaVersion, int columnIndex) {
    }
}
