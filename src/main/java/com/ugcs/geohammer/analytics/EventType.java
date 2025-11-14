package com.ugcs.geohammer.analytics;

public enum EventType {
    APP_STARTED("geohammer-app-started"),
    FILE_OPENED("geohammer-file-opened"),
    FILE_OPEN_ERROR("geohammer-file-open-error"),
    LICENSE_VALIDATED("geohammer-license-validated"),
    SCRIPT_EXECUTION_STARTED("geohammer-script-execution-started");

    private final String code;

    EventType(String type) {
        this.code = type;
    }

    public String getCode() {
        return code;
    }
}
