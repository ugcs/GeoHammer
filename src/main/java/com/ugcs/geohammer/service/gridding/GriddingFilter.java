package com.ugcs.geohammer.service.gridding;

import com.ugcs.geohammer.model.Range;

public record GriddingFilter(
        Range range,
        boolean analyticSignal,
        boolean hillShading,
        boolean smoothing
) {
}
