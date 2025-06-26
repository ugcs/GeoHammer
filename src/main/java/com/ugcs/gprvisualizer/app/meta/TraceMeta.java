package com.ugcs.gprvisualizer.app.meta;

import com.google.gson.annotations.Expose;

import java.util.List;

public class TraceMeta {

    @Expose
    private SampleRange sampleRange;

    @Expose
    private List<TraceLine> lines;

    @Expose
    private List<TraceMark> marks;

    public SampleRange getSampleRange() {
        return sampleRange;
    }

    public void setSampleRange(SampleRange sampleRange) {
        this.sampleRange = sampleRange;
    }

    public List<TraceLine> getLines() {
        return lines;
    }

    public void setLines(List<TraceLine> lines) {
        this.lines = lines;
    }

    public List<TraceMark> getMarks() {
        return marks;
    }

    public void setMarks(List<TraceMark> marks) {
        this.marks = marks;
    }
}
