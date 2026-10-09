package com.ugcs.geohammer.service.magnetics;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public record HeadingCorrectionResult(List<@Nullable Number> values, List<HeadingCrossover> crossovers,
                                      Map<Integer, Double> corrections, double rmsError) {
}
