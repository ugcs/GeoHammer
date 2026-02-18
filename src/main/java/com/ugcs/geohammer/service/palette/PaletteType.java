package com.ugcs.geohammer.service.palette;

import org.jspecify.annotations.NonNull;

import java.util.Objects;

public enum PaletteType {
    LINEAR("Linear"),
    GAUSSIAN("Gaussian"),
    HISTOGRAM("Histogram");

    private final @NonNull String displayName;

    PaletteType(@NonNull String displayName) {
        this.displayName = displayName;
    }

    public @NonNull String getDisplayName() {
        return displayName;
    }

    public static PaletteType defaultPaletteType() {
        return PaletteType.GAUSSIAN;
    }

    public static PaletteType findByName(String name) {
        for (PaletteType value : PaletteType.values()) {
            if (Objects.equals(value.name(), name)) {
                return value;
            }
        }
        return defaultPaletteType();
    }
}
