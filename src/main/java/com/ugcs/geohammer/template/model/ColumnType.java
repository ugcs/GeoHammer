package com.ugcs.geohammer.template.model;

public enum ColumnType {

    NONE(""),
    LATITUDE("Latitude"),
    LONGITUDE("Longitude"),
    ALTITUDE("Altitude"),
    DATE_TIME("Date-time"),
    DATE("Date"),
    TIME("Time"),
    TIMESTAMP("Timestamp"),
    LINE("Line"),
    VALUE("Value");

    private final String label;

    ColumnType(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
