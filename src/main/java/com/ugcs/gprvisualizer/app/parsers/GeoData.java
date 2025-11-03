package com.ugcs.gprvisualizer.app.parsers;

import com.ugcs.gprvisualizer.utils.Check;
import com.ugcs.gprvisualizer.utils.Nulls;
import com.ugcs.gprvisualizer.utils.Strings;

import java.util.List;

public class GeoData extends GeoCoordinates {

    // values layout schema
    private final ColumnSchema schema;

    // contains numbers for parsed data values
    // and strings for unparsed values
    private final Object[] values;

    public GeoData(ColumnSchema schema) {
        Check.notNull(schema);

        this.schema = schema;
        this.values = new Object[schema.numColumns()];
    }

    public GeoData(ColumnSchema schema, GeoData other) {
        super(other);

        Check.notNull(schema);
        Check.notNull(other);

        this.schema = schema;
        this.values = new Object[schema.numColumns()];

        // copy values based on a new schema
        for (Column column : schema) {
            String header = column.getHeader();
            setValue(header, other.getValue(header));
        }
    }

    public ColumnSchema getSchema() {
        return schema;
    }

    public static ColumnSchema getSchema(List<GeoData> values) {
        if (Nulls.isNullOrEmpty(values)) {
            return null;
        }
        return values.getFirst().getSchema();
    }

    // access by index

    public Object getValue(int index) {
        return index >= 0 && index < values.length ? values[index] : null;
    }

    public String getString(int index) {
        return getValue(index) instanceof String s ? s : null;
    }

    public Number getNumber(int index) {
        return getValue(index) instanceof Number n ? n : null;
    }

    public void setValue(int index, Object value) {
        Check.condition(index >= 0 && index < values.length,
                "Index out of bounds");
        values[index] = value;
    }

    // access by header

    public int getValueIndex(String header) {
        if (schema == null) {
            return -1;
        }
        return schema.getColumnIndex(header);
    }

    public Object getValue(String header) {
        return getValue(getValueIndex(header));
    }

    public String getString(String header) {
        return getString(getValueIndex(header));
    }

    public Number getNumber(String header) {
        return getNumber(getValueIndex(header));
    }

    public void setValue(String header, Object value) {
        setValue(getValueIndex(header), value);
    }

    // access by semantic

    public int getValueIndexBySemantic(String semantic) {
        if (schema == null) {
            return -1;
        }
        String header = schema.getHeaderBySemantic(semantic);
        return schema.getColumnIndex(header);
    }

    public Object getValueBySemantic(String semantic) {
        return getValue(getValueIndexBySemantic(semantic));
    }

    public String getStringBySemantic(String semantic) {
        return getString(getValueIndexBySemantic(semantic));
    }

    public Number getNumberBySemantic(String semantic) {
        return getNumber(getValueIndexBySemantic(semantic));
    }

    public void setValueBySemantic(String semantic, Object value) {
        setValue(getValueIndexBySemantic(semantic), value);
    }

    // common semantics

    public Integer getLine() {
        Number line = getNumberBySemantic(Semantic.LINE.getName());
        return line != null ? line.intValue() : null;
    }

    public int getLineOrDefault(int defaultLine) {
        Integer line = getLine();
        return line != null ? line : defaultLine;
    }

    public void setLine(Integer line) {
        setValueBySemantic(Semantic.LINE.getName(), line);
    }

    public Boolean getMark() {
        Number mark = getNumberBySemantic(Semantic.MARK.getName());
        return mark != null ? mark.intValue() != 0 : null;
    }

    public boolean getMarkOrDefault(boolean defaultMark) {
        Boolean mark = getMark();
        return mark != null ? mark : defaultMark;
    }

    public void setMark(Boolean mark) {
        Number value = mark != null
                ? mark ? 1 : 0
                : null;
        setValueBySemantic(Semantic.MARK.getName(), value);
    }
}
