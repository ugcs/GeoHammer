package com.ugcs.geohammer.format;

import com.ugcs.geohammer.model.Column;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.model.Semantic;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Nulls;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

public class GeoData {

    // values layout schema
    private final ColumnSchema schema;

    // contains numbers for parsed data values
    // and strings for unparsed values
    private Object[] values;

    // -1 for empty value
    private long timestamp = -1;

    public GeoData(ColumnSchema schema) {
        Check.notNull(schema);

        this.schema = schema;
        this.values = new Object[schema.numColumns()];
    }

    public GeoData(ColumnSchema schema, GeoData other) {
        Check.notNull(schema);
        Check.notNull(other);

        this.schema = schema;
        this.values = new Object[schema.numColumns()];
        this.timestamp = other.timestamp;

        // copy values based on a new schema
        for (Column column : schema) {
            String header = column.getHeader();
            setValue(header, other.getValue(header));
        }
    }

    public ColumnSchema getSchema() {
        return schema;
    }

    public LocalDateTime getDateTime() {
        return timestamp != -1
                ? LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC)
                : null;
    }

    public void setDateTime(LocalDateTime dateTime) {
        timestamp = dateTime != null
                ? dateTime.toInstant(ZoneOffset.UTC).toEpochMilli()
                : -1;
    }

    private void ensureCapacity() {
        if (values.length < schema.numColumns()) {
            values = Arrays.copyOf(values, schema.numColumns());
        }
    }

    private void removeValue(int index) {
        int n = Math.min(values.length, schema.numColumns());
        for (int i = index; i < n - 1; i++) {
            values[i] = values[i + 1];
        }
        if (n > 0) {
            values[n - 1] = null;
        }
    }

    // access by index

    public Object getValue(int index) {
        int n = Math.min(values.length, schema.numColumns());
        return index >= 0 && index < n ? values[index] : null;
    }

    public String getString(int index) {
        return getValue(index) instanceof String s ? s : null;
    }

    public Number getNumber(int index) {
        return getValue(index) instanceof Number n ? n : null;
    }

    public void setValue(int index, Object value) {
        int n = Math.min(values.length, schema.numColumns());
        Check.condition(index >= 0 && index < n,
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

    public LatLon getLatLon() {
        Double latitude = getLatitude();
        Double longitude = getLongitude();
        return latitude != null && longitude != null
                ? new LatLon(latitude, longitude)
                : null;
    }

    public void setLatLon(LatLon latLon) {
        setLatitude(latLon != null ? latLon.getLatDgr() : null);
        setLongitude(latLon != null ? latLon.getLonDgr() : null);
    }

    public Double getLatitude() {
        Number latitude = getNumberBySemantic(Semantic.LATITUDE.getName());
        return latitude != null ? latitude.doubleValue() : null;
    }

    public void setLatitude(Double latitude) {
        setValueBySemantic(Semantic.LATITUDE.getName(), latitude);
    }

    public Double getLongitude() {
        Number longitude = getNumberBySemantic(Semantic.LONGITUDE.getName());
        return longitude != null ? longitude.doubleValue() : null;

    }

    public void setLongitude(Double longitude) {
        setValueBySemantic(Semantic.LONGITUDE.getName(), longitude);
    }

    public Double getAltitude() {
        Number altitude = getNumberBySemantic(Semantic.ALTITUDE.getName());
        return altitude != null ? altitude.doubleValue() : null;
    }

    public void setAltitude(@Nullable Double altitude) {
        setValueBySemantic(Semantic.ALTITUDE.getName(), altitude);
    }

	public boolean hasAltitudeSemantic() {
		return schema.getHeaderBySemantic(Semantic.ALTITUDE.getName()) != null;
	}

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

    // columns modification

    public static ColumnSchema getSchema(List<GeoData> values) {
        if (Nulls.isNullOrEmpty(values)) {
            return null;
        }
        return values.getFirst().getSchema();
    }

    public static Column getColumn(List<GeoData> values, String header) {
        ColumnSchema schema = getSchema(values);
        if (schema == null) {
            return null;
        }
        return schema.getColumn(header);
    }

    public static Column addColumn(List<GeoData> values, String header) {
        return addColumn(values, new Column(header));
    }

    public static Column addColumn(List<GeoData> values, Column newColumn) {
        ColumnSchema schema = getSchema(values);
        if (schema == null) {
            return null;
        }
        Column column = schema.getColumn(newColumn.getHeader());
        if (column != null) {
            return null;
        }
        schema.addColumn(newColumn);
        // resize values array if necessary
        for (GeoData value : values) {
            value.ensureCapacity();
        }
        return newColumn;
    }

    public static Column removeColumn(List<GeoData> values, String header) {
        ColumnSchema schema = getSchema(values);
        if (schema == null) {
            return null;
        }
        int columnIndex = schema.getColumnIndex(header);
        if (columnIndex == -1) {
            return null;
        }
        // drop value in values array
        for (GeoData value : values) {
            value.removeValue(columnIndex);
        }
        return schema.removeColumn(header);
    }

    public static Column removeColumn(List<GeoData> values, Column column) {
        return removeColumn(values, column.getHeader());
    }
}
