package com.ugcs.geohammer.service.palette;

import org.jspecify.annotations.NonNull;

import java.util.Objects;

public enum SpectrumType {
    HUE("Hue"),
    RAINBOW("Rainbow"),
    DIVERGING_RAINBOW("Diverging Rainbow"),
    BLUE_WHITE_RED("Blue-White-Red"),
    THERMAL("Thermal"),
    TERRAIN("Terrain"),
    WATER_DEPTH("Water Depth"),
    VIRIDIS("Viridis"),
    MAGMA("Magma"),
    ISOLUMINANT("Isoluminant"),
    GRAYSCALE("Grayscale");

    private final @NonNull String displayName;

    SpectrumType(@NonNull String displayName) {
        this.displayName = displayName;
    }

    public @NonNull String getDisplayName() {
        return displayName;
    }

    public static SpectrumType defaultSpectrumType() {
        return SpectrumType.RAINBOW;
    }

    public static SpectrumType findByName(String name) {
        for (SpectrumType value : SpectrumType.values()) {
            if (Objects.equals(value.name(), name)) {
                return value;
            }
        }
        return defaultSpectrumType();
    }
}
