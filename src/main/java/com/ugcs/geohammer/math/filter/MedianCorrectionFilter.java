package com.ugcs.geohammer.math.filter;

import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Nulls;

import java.util.ArrayList;
import java.util.List;

public class MedianCorrectionFilter implements SequenceFilter {

    private final int window;

    public MedianCorrectionFilter(int window) {
        Check.condition(window > 0);
        this.window = window;
    }

    @Override
    public List<Number> apply(List<Number> values) {
        if (Nulls.isNullOrEmpty(values)) {
            return values;
        }

        int n = values.size();
        // number of window elements on the right from current
        // (including current element)
        int h = Math.min((window + 1) / 2, n);

        LazyRunningMedian runningMedian = new LazyRunningMedian(window);
        // init: put first h - 1 elements to the window
        for (int i = 0; i < h - 1; i++) {
            runningMedian.add(values.get(i));
        }

        List<Number> filtered = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            // update window
            // next element to put to a window
            int next = i + h - 1;
            if (next < n) {
                runningMedian.add(values.get(next));
            } else {
                // no new elements for a window
                // shrink window size by 1
                runningMedian.growWindow(-1);
            }

            // apply median correction
            Number value = values.get(i);
            if (value != null) {
                Number median = runningMedian.median();
                if (median != null) {
                    value = value.doubleValue() - median.doubleValue();
                }
            }
            filtered.add(value);
        }
        return filtered;
    }
}
