package com.github.thecoldwine.sigrun.common.ext;

import com.ugcs.gprvisualizer.app.parsers.GeoData;

import java.util.ArrayList;

public class TraceGeoData extends GeoData {

    private int traceIndex;

    public TraceGeoData(int traceIndex) {
        super(0, 0);

        setSensorValues(new ArrayList<>());
        this.traceIndex = traceIndex;
    }

    public int getTraceIndex() {
        return traceIndex;
    }

    public void setTraceIndex(int traceIndex) {
        this.traceIndex = traceIndex;
    }
}
