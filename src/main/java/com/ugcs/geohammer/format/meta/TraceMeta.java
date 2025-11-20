package com.ugcs.geohammer.format.meta;

import com.google.gson.annotations.Expose;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.model.Range;

import java.util.List;

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

	public Range getAmplitudeRange() {
		return amplitudeRange;
	}

	public void setAmplitudeRange(Range amplitudeRange) {
		this.amplitudeRange = amplitudeRange;
	}
}
