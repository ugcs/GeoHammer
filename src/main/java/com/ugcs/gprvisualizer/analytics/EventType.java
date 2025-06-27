package com.ugcs.gprvisualizer.analytics;

public enum EventType {
    APP_STARTED("geohammer-app-started"),
    ;

    private final String code;

    EventType(String type) {
        this.code = type;
    }

    public String getCode() {
        return code;
    }
}
