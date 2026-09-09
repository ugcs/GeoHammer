package com.ugcs.geohammer.math;

import java.util.Collections;
import java.util.List;
import java.util.function.ToDoubleFunction;

public class QuickSelect {

    private QuickSelect() {
    }

    public static <T> T select(List<T> values, ToDoubleFunction<T> f, int rank) {
        int l = 0;
        int r = values.size() - 1;
        while (l < r) {
            double pivot = medianOfThree(values, f, l, r);
            // [l, j] <= pivot, [i, r] >= pivot,
            // and anything strictly between j and i equals the pivot
            int i = l;
            int j = r;
            while (i <= j) {
                while (f.applyAsDouble(values.get(i)) < pivot) {
                    i++;
                }
                while (f.applyAsDouble(values.get(j)) > pivot) {
                    j--;
                }
                if (i <= j) {
                    Collections.swap(values, i, j);
                    i++;
                    j--;
                }
            }
            if (rank <= j) {
                r = j;
            } else if (rank >= i) {
                l = i;
            } else {
                return values.get(rank);
            }
        }
        return values.get(l);
    }

    private static <T> double medianOfThree(List<T> values, ToDoubleFunction<T> f, int l, int r) {
        double a = f.applyAsDouble(values.get(l));
        double b = f.applyAsDouble(values.get((l + r) >>> 1));
        double c = f.applyAsDouble(values.get(r));
        // do not change to clamp
        return Math.max(Math.min(a, b), Math.min(Math.max(a, b), c));
    }
}
