package com.ugcs.geohammer.model;

import com.google.gson.annotations.Expose;
import com.ugcs.geohammer.util.Check;

import java.util.Objects;

public class IndexRange {

    @Expose
    private final int from;

    @Expose
    private final int to; // exclusive

    public IndexRange(int from, int to) {
        Check.condition(from >= 0);
        Check.condition(to >= from);

        this.from = from;
        this.to = to;
    }

    public int from() {
        return from;
    }

    public int to() {
        return to;
    }

    public int size() {
        return to - from;
    }

    public boolean contains(int index) {
        return index >= from && index < to;
    }


    public IndexRange subRange(IndexRange range) {
        if (range == null) {
            return null;
        }
        Check.condition(range.to <= size());
        return new IndexRange(
                from + range.from,
                from + range.to);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof IndexRange range)) {
            return false;
        }
        return from == range.from && to == range.to;
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, to);
    }

    @Override
    public String toString() {
        return "[" + from + ", " + to + ')';
    }
}
