package com.ugcs.gprvisualizer.utils;

import com.ugcs.gprvisualizer.app.auxcontrol.BaseObject;
import com.ugcs.gprvisualizer.app.auxcontrol.FoundPlace;
import com.ugcs.gprvisualizer.app.auxcontrol.Positional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AuxElements {

    private AuxElements() {
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
