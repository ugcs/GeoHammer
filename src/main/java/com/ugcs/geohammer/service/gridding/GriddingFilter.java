package com.ugcs.geohammer.service.gridding;

import com.ugcs.geohammer.model.Range;
import com.ugcs.geohammer.service.palette.PaletteType;
import com.ugcs.geohammer.service.palette.SpectrumType;

public record GriddingFilter(
        Range range,
        boolean analyticSignal,
        boolean hillShading,
        boolean smoothing,
        PaletteType paletteType,
        SpectrumType spectrumType
) {
}
