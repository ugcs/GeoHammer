package com.ugcs.geohammer.service.magnetics;

import com.ugcs.geohammer.util.Check;

public record DiurnalCorrectionOptions(
        String inputSeries,
        String outputSeries,
        String baseStationSeries,
        Double referenceField
) {

    public DiurnalCorrectionOptions {
        Check.notEmpty(inputSeries);
        Check.notEmpty(outputSeries);
        Check.notEmpty(baseStationSeries);
        Check.condition(referenceField == null || Double.isFinite(referenceField),
                "Reference field must be finite");
    }
}
