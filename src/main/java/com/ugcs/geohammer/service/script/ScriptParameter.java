package com.ugcs.geohammer.service.script;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ScriptParameter(
        String name,
        @JsonProperty("display_name")
        String displayName,
        ParameterType type,
        @JsonProperty("default_value")
        String defaultValue,
        boolean required,
        @JsonProperty("enum_values")
        List<String> enumValues
) {
    public enum ParameterType {
        STRING, INTEGER, DOUBLE, BOOLEAN, FILE_PATH, FOLDER_PATH, COLUMN_NAME, ENUM
    }
}
