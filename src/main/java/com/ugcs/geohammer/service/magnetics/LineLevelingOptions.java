package com.ugcs.geohammer.service.magnetics;

import com.ugcs.geohammer.util.Check;

public record LineLevelingOptions(
        String inputSeries,
        String outputSeries
) {

    public LineLevelingOptions {
        Check.notEmpty(inputSeries);
        Check.notEmpty(outputSeries);
    }
}
