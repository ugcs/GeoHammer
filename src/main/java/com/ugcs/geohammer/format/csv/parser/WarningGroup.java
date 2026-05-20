package com.ugcs.geohammer.format.csv.parser;

import org.jspecify.annotations.NonNull;

public record WarningGroup(String column, String message, int count) {

    @Override
    public @NonNull String toString() {
        return column + " (" + count + (count == 1 ? " error): " : " errors): ") + message;
    }
}
