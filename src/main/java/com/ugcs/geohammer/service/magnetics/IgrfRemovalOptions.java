package com.ugcs.geohammer.service.magnetics;

import com.ugcs.geohammer.util.Check;

import java.time.Instant;

public record IgrfRemovalOptions(
        String inputSeries,
        String outputSeries,
        Instant fallbackTimestamp
) {

    public IgrfRemovalOptions {
        Check.notEmpty(inputSeries);
        Check.notEmpty(outputSeries);
    }
}
