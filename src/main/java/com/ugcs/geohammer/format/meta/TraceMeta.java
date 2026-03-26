package com.ugcs.geohammer.format.meta;

import com.google.gson.annotations.Expose;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.model.Range;

import java.util.List;

import org.jspecify.annotations.Nullable;

public class TraceMeta {

    @Expose
    private IndexRange sampleRange;

    @Expose
    private List<TraceLine> lines;

    @Expose
    private List<TraceMark> marks;

    @Expose
    private Double contrast;

    @Expose
    private IndexRange depthRange;

    @Deprecated
    @Expose(serialize = false)
    private Range amplitudeRange;

    public IndexRange getSampleRange() {
        return sampleRange;
    }

    public void setSampleRange(IndexRange sampleRange) {
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

    public Double getContrast() {
        return contrast;
    }

    public void setContrast(Double contrast) {
        this.contrast = contrast;
    }

    @Nullable
    public IndexRange getDepthRange() {
        if (depthRange != null) {
            return depthRange;
        }
        if (amplitudeRange != null) {
            return new IndexRange((int) amplitudeRange.getMin(), (int) amplitudeRange.getMax());
        }
        return null;
    }

    public void setDepthRange(@Nullable IndexRange depthRange) {
        this.depthRange = depthRange;
    }
}
