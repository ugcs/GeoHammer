package com.ugcs.geohammer.format.meta;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.util.Check;

public class TraceGeoData extends GeoData {

    private int traceIndex;

    public TraceGeoData(ColumnSchema schema, int traceIndex) {
        super(schema);

        this.traceIndex = traceIndex;
    }

    public TraceGeoData(ColumnSchema schema, TraceGeoData other) {
        super(schema, other);

        Check.notNull(other);
        this.traceIndex = other.traceIndex;
    }

    public int getTraceIndex() {
        return traceIndex;
    }

    public void setTraceIndex(int traceIndex) {
        this.traceIndex = traceIndex;
    }
}
