package com.ugcs.geohammer.service.magnetics;

import com.ugcs.geohammer.util.Check;

public record HeadingCorrectionOptions(String inputSeries, String outputSeries, int headingBins) {

    public HeadingCorrectionOptions {
        Check.notEmpty(inputSeries);
        Check.notEmpty(outputSeries);
        Check.condition(headingBins >= 4 && headingBins <= 36, "Heading correction requires between 4 and 36 bins");
    }
}
