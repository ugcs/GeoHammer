package com.ugcs.geohammer.service.magnetics;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public record CrossoverLevelingResult(
        List<@Nullable Number> values,
        List<Crossover> crossovers,
        Map<Integer, Double> corrections,
        double rmsError
) {

    public CrossoverLevelingResult {
        values = List.copyOf(values);
        crossovers = List.copyOf(crossovers);
        corrections = Map.copyOf(corrections);
    }
}
