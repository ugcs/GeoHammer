package com.ugcs.gprvisualizer.utils;

import com.ugcs.gprvisualizer.app.auxcontrol.BaseObject;
import com.ugcs.gprvisualizer.app.auxcontrol.FoundPlace;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AuxElements {

    private AuxElements() {
    }

    public static Set<Integer> getMarkIndices(List<BaseObject> elements) {
        Set<Integer> markIndices = new HashSet<>();
        for (BaseObject element : Nulls.toEmpty(elements)) {
            if (element instanceof FoundPlace mark) {
                markIndices.add(mark.getTraceInFile());
            }
        }
        return markIndices;
    }
}
