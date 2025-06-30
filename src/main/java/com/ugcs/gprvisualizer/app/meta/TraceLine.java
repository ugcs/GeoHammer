package com.ugcs.gprvisualizer.app.meta;

import com.google.gson.annotations.Expose;

public class TraceLine {

    @Expose
    private int lineIndex;

    @Expose
    private int from;

    @Expose
    private int to;

    public Integer getLineIndex() {
        return lineIndex;
    }

    public void setLineIndex(Integer lineIndex) {
        this.lineIndex = lineIndex;
    }

    public Integer getFrom() {
        return from;
    }

    public void setFrom(Integer from) {
        this.from = from;
    }

    public Integer getTo() {
        return to;
    }

    public void setTo(Integer to) {
        this.to = to;
    }
}
