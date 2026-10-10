package com.ugcs.geohammer.service.quality;

import java.util.List;

public record SurveyGeometryReport(
        int lineCount,
        int primaryLineCount,
        int tieLineCount,
        Distribution sampleSpacing,
        Distribution primaryLineSpacing,
        Distribution tieLineSpacing,
        Distribution heading,
        List<Double> sampleSpacingValues,
        List<Double> primaryLineSpacingValues,
        List<Double> tieLineSpacingValues,
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
