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
        boolean analyticSignalEnabled,
        boolean hillShadingEnabled,
        boolean smoothingEnabled
) {

    public GriddingResult setValues(
            float minValue,
            float maxValue,
            boolean analyticSignalEnabled,
            boolean hillShadingEnabled,
            boolean smoothingEnabled
    ) {
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
                smoothingEnabled
        );
    }
}