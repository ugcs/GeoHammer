package com.ugcs.gprvisualizer.analytics;

public enum EventType {
    APP_STARTED("geohammer-app-started"),
    FILE_OPENED("geohammer-file-opened"),
    FILE_OPEN_ERROR("geohammer-file-open-error");

    private final String code;

    EventType(String type) {
        this.code = type;
    }

    public String getCode() {
        return code;
    }
}
