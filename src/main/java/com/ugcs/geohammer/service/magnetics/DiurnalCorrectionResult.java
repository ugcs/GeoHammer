package com.ugcs.geohammer.service.magnetics;

import org.jspecify.annotations.Nullable;

import java.util.List;

public record DiurnalCorrectionResult(List<@Nullable Number> values, double referenceField) {

    public DiurnalCorrectionResult {
        values = List.copyOf(values);
    }
}
