package com.ugcs.geohammer.service.magnetics;

import org.jspecify.annotations.Nullable;

import java.util.List;

public record RegionalRemovalResult(List<@Nullable Number> values, int sampleCount, double rmsError) {
}
