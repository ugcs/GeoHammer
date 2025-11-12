package com.ugcs.geohammer.service.gridding;

import com.ugcs.geohammer.model.LatLon;

public record GriddingResult(
        float[][] gridData,
        float[][] smoothedGridData,
        LatLon minLatLon,
        LatLon maxLatLon,
        double cellSize,
        double blankingDistance,
        float minValue,
        float maxValue,
        String sensor,
        // Analytic signal
        boolean analyticSignalEnabled,
        // Hill-shading parameters
        boolean hillShadingEnabled,
        boolean smoothingEnabled,
        double hillShadingAzimuth,
        double hillShadingAltitude,
        double hillShadingIntensity) {

    public GriddingResult setValues(float minValue,
                                    float maxValue,
                                    boolean analyticSignalEnabled,
                                    boolean hillShadingEnabled,
                                    boolean smoothingEnabled) {
        return new GriddingResult(
                gridData,
                smoothedGridData,
                minLatLon,
                maxLatLon,
                cellSize,
                blankingDistance,
                minValue,
                maxValue,
                sensor,
                analyticSignalEnabled,
                hillShadingEnabled,
                smoothingEnabled,
                hillShadingAzimuth,
                hillShadingAltitude,
                hillShadingIntensity
        );
    }
}