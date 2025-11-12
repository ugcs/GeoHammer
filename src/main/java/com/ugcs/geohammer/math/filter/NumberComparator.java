package com.ugcs.geohammer.math.filter;

import java.util.Comparator;

public class NumberComparator implements Comparator<Number> {

    @Override
    public int compare(Number x, Number y) {
        return Double.compare(x.doubleValue(), y.doubleValue());
    }
}
