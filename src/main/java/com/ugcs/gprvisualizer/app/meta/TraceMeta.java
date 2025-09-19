package com.ugcs.gprvisualizer.app.meta;

import com.google.gson.annotations.Expose;
import com.ugcs.gprvisualizer.utils.Range;

import java.util.List;

public class TraceMeta {

    @Expose
    private SampleRange sampleRange;

    @Expose
    private List<TraceLine> lines;

    @Expose
    private List<TraceMark> marks;

	@Expose
	private Double contrast;

	@Expose
	private Range amplitudeMapLevels;

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

	public Double getContrast() {
		return contrast;
	}

	public void setContrast(Double contrast) {
		this.contrast = contrast;
	}

	public Range getAmplitudeMapLevels() {
		return amplitudeMapLevels;
	}

	public void setAmplitudeMapLevels(Range amplitudeMapLevels) {
		this.amplitudeMapLevels = amplitudeMapLevels;
	}
}
