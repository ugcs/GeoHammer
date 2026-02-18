package com.ugcs.geohammer.service.gridding;

import com.ugcs.geohammer.model.LatLon;

public record GriddingResult(
        String seriesName,
        float[][] grid,
        LatLon minLatLon,
        LatLon maxLatLon,
        GriddingParams params
) {
}