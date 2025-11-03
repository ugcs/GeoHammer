package com.github.thecoldwine.sigrun.common.ext;

import com.ugcs.gprvisualizer.app.parsers.Column;
import com.ugcs.gprvisualizer.app.parsers.ColumnSchema;
import com.ugcs.gprvisualizer.app.parsers.GeoData;
import com.ugcs.gprvisualizer.app.parsers.Semantic;

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
