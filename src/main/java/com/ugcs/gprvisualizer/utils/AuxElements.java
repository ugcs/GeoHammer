package com.ugcs.gprvisualizer.utils;

import com.ugcs.gprvisualizer.app.auxcontrol.BaseObject;
import com.ugcs.gprvisualizer.app.auxcontrol.FoundPlace;
import com.ugcs.gprvisualizer.app.auxcontrol.PositionalObject;
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
            if (range != null && element instanceof PositionalObject positional) {
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
        return getMarkIndices(elements, null);
    }

    public static Set<Integer> getMarkIndices(List<BaseObject> elements, IndexRange range) {
        Set<Integer> markIndices = new HashSet<>();
        for (BaseObject element : Nulls.toEmpty(elements)) {
            if (element instanceof FoundPlace mark) {
                int traceIndex = mark.getTraceIndex();
                if (range != null) {
                    if (traceIndex < range.from() || traceIndex >= range.to()) {
                        continue;
                    }
                }
                markIndices.add(traceIndex);
            }
        }
        return markIndices;
    }

    public static Map<Integer, List<PositionalObject>> getPositionalElements(List<BaseObject> elements) {
        Map<Integer, List<PositionalObject>> index = new HashMap<>();
        for (BaseObject element : Nulls.toEmpty(elements)) {
            if (element instanceof PositionalObject positional) {
                int traceIndex = positional.getTraceIndex();
                index.computeIfAbsent(traceIndex, k -> new ArrayList<>()).add(positional);
            }
        }
        return index;
    }
}
