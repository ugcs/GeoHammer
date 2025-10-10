package com.ugcs.gprvisualizer.app.scripts;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ScriptParameter(
        String name,
        @JsonProperty("display_name")
        String displayName,
        ParameterType type,
        @JsonProperty("default_value")
        String defaultValue,
        boolean required
) {
    public enum ParameterType {
        STRING, INTEGER, DOUBLE, BOOLEAN, FILE_PATH
    }
}
