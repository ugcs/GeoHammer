package com.ugcs.geohammer.service.magnetics;

import com.ugcs.geohammer.util.Check;

import java.time.Instant;
import java.util.Map;

public record MagneticProcessingStep(
        MagneticProcessingStepType type,
        String inputSeries,
        String outputSeries,
        Map<String, String> settings,
        Instant createdAt
) {

    public MagneticProcessingStep {
        Check.notNull(type);
        Check.notEmpty(inputSeries);
        Check.notEmpty(outputSeries);
        Check.notNull(settings);
        Check.notNull(createdAt);

        settings = Map.copyOf(settings);
    }
}
