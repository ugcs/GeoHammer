package com.ugcs.geohammer.template.model;

public enum TimeReference {
    UTC("UTC Time"),
    GPS("GPS Time");

    private final String displayName;

    TimeReference(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
