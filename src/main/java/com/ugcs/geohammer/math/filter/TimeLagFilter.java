package com.ugcs.geohammer.math.filter;

import com.ugcs.geohammer.util.Nulls;

import java.util.Arrays;
import java.util.List;

public class TimeLagFilter implements SequenceFilter {

    private final int shift;

    public TimeLagFilter(int shift) {
        this.shift = shift;
    }

    @Override
    public List<Number> apply(List<Number> values) {
        if (Nulls.isNullOrEmpty(values)) {
            return values;
        }

        Number[] filtered = new Number[values.size()];

        int l = 0;
        int r = values.size() - 1;

        // shift > 0 -> move left
        if (shift > 0) {
            l += shift;
        } else {
            // shift is negative
            r += shift;
        }
        for (int i = l; i <= r; i++) {
            int j = i - shift;
            if (j >= 0 && j < filtered.length) {
                filtered[j] = values.get(i);
            }
        }
        return Arrays.asList(filtered);
    }
}
