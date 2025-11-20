package com.ugcs.geohammer.chart.tool;

import java.util.function.Consumer;
import java.util.function.Predicate;

class FilterActions {
    Predicate<String> constraint = v -> true;
    Consumer<String> apply;
    Consumer<String> applyAll;

    boolean hasApply() {
        return apply != null;
    }

    boolean hasApplyAll() {
        return applyAll != null;
    }
}
