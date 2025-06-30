package com.ugcs.gprvisualizer.app.meta;

import com.google.gson.annotations.Expose;

public class TraceMark {

    @Expose
    private int traceIndex;

    public Integer getTraceIndex() {
        return traceIndex;
    }

    public void setTraceIndex(Integer traceIndex) {
        this.traceIndex = traceIndex;
    }
}
