package com.ugcs.geohammer.format;

import com.ugcs.geohammer.model.Column;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.Semantic;

public class TraceGeoData extends GeoData {

    private static final ColumnSchema SCHEMA = getTraceSchema();

    private int traceIndex;

    private static ColumnSchema getTraceSchema() {
        ColumnSchema schema = new ColumnSchema();
        // line
        schema.addColumn(new Column(Semantic.LINE.getName())
                .withSemantic(Semantic.LINE.getName())
                .withDisplay(true));
        return schema;
    }

    public TraceGeoData(int traceIndex) {
        super(SCHEMA);

        this.traceIndex = traceIndex;
    }

    public int getTraceIndex() {
        return traceIndex;
    }

    public void setTraceIndex(int traceIndex) {
        this.traceIndex = traceIndex;
    }
}
