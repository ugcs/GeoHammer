package com.ugcs.geohammer.service.magnetics;

import com.ugcs.geohammer.util.Check;

public record LineLevelingOptions(
        String inputSeries,
        String outputSeries,
        String tieLineSeries,
        boolean applyMicroLeveling
) {

    public LineLevelingOptions {
        Check.notEmpty(inputSeries);
        Check.notEmpty(outputSeries);
        Check.notEmpty(tieLineSeries);
    }
}
