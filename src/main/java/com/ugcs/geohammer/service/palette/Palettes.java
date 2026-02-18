package com.ugcs.geohammer.service.palette;

import com.ugcs.geohammer.model.Range;

public final class Palettes {

    // gray scale with slightly reduced contrast
    public static final Spectrum CET_L02
            = GeosoftTable.loadSpectrum("colormaps/CET-L02.tbl");

    // blue-magenta-yellow highly saturated
    public static final Spectrum CET_L08
            = GeosoftTable.loadSpectrum("colormaps/CET-L08.tbl");

    // lighter geographical
    public static final Spectrum CET_L11
            = GeosoftTable.loadSpectrum("colormaps/CET-L11.tbl");

    // water depth
    public static final Spectrum CET_L12
            = GeosoftTable.loadSpectrum("colormaps/CET-L12.tbl");

    // diverging blue-white-red
    public static final Spectrum CET_D01
            = GeosoftTable.loadSpectrum("colormaps/CET-D01.tbl");

    // rainbow
    public static final Spectrum CET_R1
            = GeosoftTable.loadSpectrum("colormaps/CET-R1.tbl");

    // rainbow-diverging
    public static final Spectrum CET_R3
            = GeosoftTable.loadSpectrum("colormaps/CET-R3.tbl");

    // isoluminant blue to green to orange at lightness 80
    public static final Spectrum CET_I2
            = GeosoftTable.loadSpectrum("colormaps/CET-I2.tbl");

    // viridis - viridis
    public static final Spectrum VIRIDIS
            = GeosoftTable.loadSpectrum("colormaps/viridis.tbl");

    // viridis - magma
    public static final Spectrum MAGMA
            = GeosoftTable.loadSpectrum("colormaps/magma.tbl");

    private Palettes() {
	}

    public static Spectrum createSpectrum(SpectrumType spectrumType) {
        if (spectrumType == null) {
            spectrumType = SpectrumType.defaultSpectrumType();
        }
        return switch (spectrumType) {
            case HUE -> new HueGradient();
            case RAINBOW -> CET_R1;
            case DIVERGING_RAINBOW -> CET_R3;
            case BLUE_WHITE_RED -> CET_D01;
            case THERMAL -> CET_L08;
            case TERRAIN -> CET_L11;
            case WATER_DEPTH -> CET_L12;
            case VIRIDIS -> VIRIDIS;
            case MAGMA -> MAGMA;
            case ISOLUMINANT -> CET_I2;
            case GRAYSCALE -> CET_L02;
        };
    }

    public static Palette create(PaletteType paletteType, SpectrumType spectrumType,
            float[] sortedValues, Range range) {
        Spectrum spectrum = createSpectrum(spectrumType);
        if (paletteType == null) {
            paletteType = PaletteType.defaultPaletteType();
        }
        return switch (paletteType) {
            case LINEAR -> new LinearPalette(spectrum, range);
            case GAUSSIAN -> new GaussianPalette(spectrum, sortedValues, range);
            case HISTOGRAM -> new QuantilePalette(spectrum, sortedValues, range);
        };
    }
}
