package com.ugcs.geohammer.model.undo;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.model.Column;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.util.Nulls;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public final class GeoDataCodec {

    private static final byte TAG_NULL = 0;

    private static final byte TAG_STRING = 1;

    private static final byte TAG_DOUBLE = 2;

    private static final byte TAG_FLOAT = 3;

    private static final byte TAG_LONG = 4;

    private static final byte TAG_INTEGER = 5;

    private GeoDataCodec() {
    }

    private static void writeNullableString(DataOutput out, String s) throws IOException {
        out.writeBoolean(s != null);
        if (s != null) {
            out.writeUTF(s);
        }
    }

    private static String readNullableString(DataInput in) throws IOException {
        return in.readBoolean() ? in.readUTF() : null;
    }

    private static void writeColumn(DataOutput out, Column column) throws IOException {
        out.writeUTF(column.getHeader());
        writeNullableString(out, column.getSemantic());
        writeNullableString(out, column.getUnit());
        out.writeBoolean(column.isDisplay());
        out.writeBoolean(column.isReadOnly());
    }

    private static Column readColumn(DataInput in) throws IOException {
        Column column = new Column(in.readUTF());
        column.setSemantic(readNullableString(in));
        column.setUnit(readNullableString(in));
        column.setDisplay(in.readBoolean());
        column.setReadOnly(in.readBoolean());
        return column;
    }

    private static void writeSchema(DataOutput out, ColumnSchema schema) throws IOException {
        out.writeInt(schema.numColumns());
        for (Column column : schema) {
            writeColumn(out, column);
        }
    }

    private static ColumnSchema readSchema(DataInput in) throws IOException {
        int numColumns = in.readInt();
        ColumnSchema schema = new ColumnSchema();
        for (int i = 0; i < numColumns; i++) {
            schema.addColumn(readColumn(in));
        }
        return schema;
    }

    private static void writeTimestamp(DataOutput out, LocalDateTime dateTime) throws IOException {
        long timestamp = dateTime != null
                ? dateTime.toInstant(ZoneOffset.UTC).toEpochMilli()
                : -1;
        out.writeLong(timestamp);
    }

    private static LocalDateTime readTimestamp(DataInput in) throws IOException {
        long timestamp = in.readLong();
        return timestamp != -1
                ? LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC)
                : null;
    }

    private static void writeValue(DataOutput out, Object value) throws IOException {
        switch (value) {
            case null -> out.writeByte(TAG_NULL);
            case String v -> {
                out.writeByte(TAG_STRING);
                out.writeUTF(v);
            }
            case Double v -> {
                out.writeByte(TAG_DOUBLE);
                out.writeDouble(v);
            }
            case Float v -> {
                out.writeByte(TAG_FLOAT);
                out.writeFloat(v);
            }
            case Long v -> {
                out.writeByte(TAG_LONG);
                out.writeLong(v);
            }
            case Integer v -> {
                out.writeByte(TAG_INTEGER);
                out.writeInt(v);
            }
            default -> throw new IOException("Unsupported value type: " + value.getClass());
        }
    }

    private static Object readValue(DataInput in) throws IOException {
        byte tag = in.readByte();
        return switch (tag) {
            case TAG_NULL -> null;
            case TAG_STRING -> in.readUTF();
            case TAG_DOUBLE -> in.readDouble();
            case TAG_FLOAT -> in.readFloat();
            case TAG_LONG -> in.readLong();
            case TAG_INTEGER -> in.readInt();
            default -> throw new IOException("Unknown value tag: " + tag);
        };
    }

    private static void writeGeoData(DataOutput out, GeoData geoData) throws IOException {
        writeTimestamp(out, geoData.getDateTime());
        int numColumns = geoData.getSchema().numColumns();
        for (int i = 0; i < numColumns; i++) {
            writeValue(out, geoData.getValue(i));
        }
    }

    private static GeoData readGeoData(DataInput in, ColumnSchema schema) throws IOException {
        GeoData geoData = new GeoData(schema);
        geoData.setDateTime(readTimestamp(in));
        int numColumns = schema.numColumns();
        for (int i = 0; i < numColumns; i++) {
            geoData.setValue(i, readValue(in));
        }
        return geoData;
    }

    public static void write(DataOutput out, List<GeoData> values) throws IOException {
        if (Nulls.isNullOrEmpty(values)) {
            out.writeInt(0);
            out.writeInt(0);
            return;
        }
        ColumnSchema schema = values.getFirst().getSchema();
        writeSchema(out, schema);
        out.writeInt(values.size());
        for (GeoData value : values) {
            writeGeoData(out, value);
        }
    }

    public static List<GeoData> read(DataInput in) throws IOException {
        ColumnSchema schema = readSchema(in);
        int numRows = in.readInt();
        List<GeoData> values = new ArrayList<>(numRows);
        for (int i = 0; i < numRows; i++) {
            values.add(readGeoData(in, schema));
        }
        return values;
    }
}
