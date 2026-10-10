package com.ugcs.geohammer.service.quality;

import com.ugcs.geohammer.model.LatLon;

import java.util.List;

public record SurveyGeometryReport(
        int lineCount,
        int primaryLineCount,
        int tieLineCount,
        double primaryLineOrientation,
        double tieLineOrientation,
        List<List<LatLon>> primaryLinePaths,
        List<List<LatLon>> tieLinePaths,
        Distribution sampleSpacing,
        Distribution primaryLineSpacing,
        Distribution heading,
        List<Double> sampleSpacingValues,
        List<Double> primaryLineSpacingValues,
        List<Double> headingValues,
        double tieLinePeakSpacing,
        int tieLinePeakCount,
        List<Double> tieLineDensityPositions,
        List<Double> tieLineDensityValues,
        List<Double> tieLinePeakPositions,
        double recommendedCellSize,
        double recommendedBlankingDistance) {

    public record Distribution(int count, double minimum, double lowerQuartile, double median,
                               double upperQuartile, double maximum) {

        public boolean isEmpty() {
            return count == 0;
        }
    }
}
