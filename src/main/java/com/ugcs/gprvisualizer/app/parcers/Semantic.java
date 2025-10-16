package com.ugcs.gprvisualizer.app.parcers;

public enum Semantic {

    LINE("Line"),
    ALTITUDE_AGL("Altitude AGL"),
    TMI("TMI"),
    MARK("Mark");

    public static final String ANOMALY_SUFFIX = "_anomaly";

    private final String name;

    Semantic(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
