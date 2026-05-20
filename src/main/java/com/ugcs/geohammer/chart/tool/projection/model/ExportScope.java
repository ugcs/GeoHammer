package com.ugcs.geohammer.chart.tool.projection.model;

public enum ExportScope {

    SELECTED_LINE("Selected line"),
    SELECTED_FILE("Selected file"),
    ALL_FILES("All files");

    private final String displayName;

    ExportScope(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
