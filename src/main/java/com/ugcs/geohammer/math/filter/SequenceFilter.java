package com.ugcs.geohammer.math.filter;

import java.util.List;

public interface SequenceFilter {

    List<Number> apply(List<Number> values);
}
