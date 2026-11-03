package com.ugcs.geohammer.math.filter;

import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Nulls;

import java.util.List;

public class LowPassFilter implements SequenceFilter {

    private final int order;

    public LowPassFilter(int order) {
        Check.condition(order >= 0);
        this.order = order;
    }

    @Override
    public List<Number> apply(List<Number> values) {
        if (Nulls.isNullOrEmpty(values)) {
            return values;
        }

        // filter length is limited to the number of values
        int alignedOrder = Math.min(order, values.size());
        if (alignedOrder <= 1) {
            return values;
        }
        return new FirFilter(alignedOrder).filterList(values);
    }
}
