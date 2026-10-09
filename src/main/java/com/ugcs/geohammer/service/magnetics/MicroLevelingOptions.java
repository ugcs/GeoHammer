package com.ugcs.geohammer.service.magnetics;

import com.ugcs.geohammer.util.Check;

public record MicroLevelingOptions(String inputSeries, String outputSeries, int windowSize) {

    public MicroLevelingOptions {
        Check.notEmpty(inputSeries);
        Check.notEmpty(outputSeries);
        Check.condition(windowSize >= 3 && windowSize % 2 == 1,
                "Micro-leveling window size must be an odd number of at least 3");
    }
}
