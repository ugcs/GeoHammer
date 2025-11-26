package com.ugcs.geohammer.format.meta;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.model.Column;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.Semantic;

public class TraceGeoData extends GeoData {

    public static final ColumnSchema SCHEMA = getTraceSchema();

    private int traceIndex;

    private static ColumnSchema getTraceSchema() {
        ColumnSchema schema = new ColumnSchema();
        // line
        schema.addColumn(new Column(Semantic.LINE.getName())
                .withSemantic(Semantic.LINE.getName())
                .withDisplay(true)
                .withReadOnly(true));
        return schema;
    }

    public TraceGeoData(ColumnSchema schema, int traceIndex) {
        super(schema);
        this.traceIndex = traceIndex;
    }

    public TraceGeoData(int traceIndex) {
        this(SCHEMA, traceIndex);
    }

    public int getTraceIndex() {
        return traceIndex;
    }

    public void setTraceIndex(int traceIndex) {
        this.traceIndex = traceIndex;
    }
}
