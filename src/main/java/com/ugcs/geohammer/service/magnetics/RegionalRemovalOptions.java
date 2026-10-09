package com.ugcs.geohammer.service.magnetics;

import com.ugcs.geohammer.util.Check;

public record RegionalRemovalOptions(
        String inputSeries,
        String outputSeries,
        int polynomialOrder
) {

    public RegionalRemovalOptions {
        Check.notEmpty(inputSeries);
        Check.notEmpty(outputSeries);
        Check.condition(polynomialOrder >= 0 && polynomialOrder <= 3,
                "Polynomial order must be between 0 and 3");
    }
}
