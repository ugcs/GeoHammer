package com.ugcs.gprvisualizer.app.parsers;

public enum Semantic {

    LINE("Line"),
    ALTITUDE_AGL("Altitude AGL"),
    TMI("TMI"),
    MARK("Mark");

    private final String name;

    Semantic(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
