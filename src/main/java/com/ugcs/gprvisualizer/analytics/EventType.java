package com.ugcs.gprvisualizer.analytics;

public enum EventType {
    APP_STARTED("geohammer-app-started"),
    FILE_OPENED("geohammer-file-opened"),
    FILE_OPENED_ERROR("geohammer-file-opened-error");

    private final String code;

    EventType(String type) {
        this.code = type;
    }

    public String getCode() {
        return code;
    }
}
