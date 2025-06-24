package com.ugcs.gprvisualizer.utils;

import com.github.thecoldwine.sigrun.common.ext.TraceKey;
import com.ugcs.gprvisualizer.app.auxcontrol.BaseObject;
import com.ugcs.gprvisualizer.app.auxcontrol.ClickPlace;
import com.ugcs.gprvisualizer.app.auxcontrol.FoundPlace;
import com.ugcs.gprvisualizer.app.auxcontrol.Positional;
import io.micrometer.common.lang.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AuxElements {

    private AuxElements() {
    }

    public static List<BaseObject> copy(List<BaseObject> elements, @Nullable Range range) {
        elements = Nulls.toEmpty(elements);

        List<BaseObject> newElements = new ArrayList<>();
        for (BaseObject element : elements) {
            if (range != null && element instanceof Positional positional) {
                int fromIndex = range.getMin().intValue();
                int toIndex = range.getMax().intValue() + 1; // exclusive
                int traceIndex = positional.getTraceIndex();
                if (traceIndex < fromIndex || traceIndex >= toIndex) {
                    continue; // filter out
                }
            }
            int offset = range != null ? range.getMin().intValue() : 0;
            BaseObject copy = element.copy(offset);
            if (copy != null) {
                newElements.add(copy);
            }
        }
        return newElements;
    }

    public static List<BaseObject> copy(List<BaseObject> elements) {
        return copy(elements, null);
    }

    public static Set<Integer> getMarkIndices(List<BaseObject> elements) {
        Set<Integer> markIndices = new HashSet<>();
        for (BaseObject element : Nulls.toEmpty(elements)) {
            if (element instanceof FoundPlace mark) {
                markIndices.add(mark.getTraceIndex());
            }
        }
        return markIndices;
    }

    public static Map<Integer, List<Positional>> getPositionalElements(List<BaseObject> elements) {
        Map<Integer, List<Positional>> index = new HashMap<>();
        for (BaseObject element : Nulls.toEmpty(elements)) {
            if (element instanceof Positional positional) {
                int traceIndex = positional.getTraceIndex();
                index.computeIfAbsent(traceIndex, k -> new ArrayList<>()).add(positional);
            }
        }
        return index;
    }
}
