package com.ugcs.gprvisualizer.app.meta;

import com.google.gson.annotations.Expose;
import com.ugcs.gprvisualizer.utils.Check;

public class SampleRange {

    @Expose
    private int from;

    @Expose
    private int to; // exclusive

    public SampleRange() {
    }

    public SampleRange(int from, int to) {
        Check.condition(from >= 0);
        Check.condition(to > from);

        this.from = from;
        this.to = to;
    }

    public int getFrom() {
        return from;
    }

    public int getTo() {
        return to;
    }

    public int size() {
        return to - from;
    }

    public SampleRange subRange(SampleRange range) {
        Check.notNull(range);
        Check.condition(range.getFrom() >= 0);
        Check.condition(range.getTo() <= size());

        return new SampleRange(
                from + range.from,
                from + range.to);
    }
}
