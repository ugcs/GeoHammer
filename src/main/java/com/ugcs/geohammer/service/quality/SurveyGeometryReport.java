package com.ugcs.geohammer.service.quality;

import java.util.List;

public record SurveyGeometryReport(
        int lineCount,
        Distribution sampleSpacing,
        Distribution lineSpacing,
        Distribution heading,
        List<Double> sampleSpacingValues,
        List<Double> lineSpacingValues,
        List<Double> headingValues,
        double recommendedCellSize,
        double recommendedBlankingDistance) {

    public record Distribution(int count, double minimum, double lowerQuartile, double median,
                               double upperQuartile, double maximum) {

        public boolean isEmpty() {
            return count == 0;
        }
    }
}
