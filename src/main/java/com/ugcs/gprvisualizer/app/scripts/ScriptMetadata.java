package com.ugcs.gprvisualizer.app.scripts;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ScriptMetadata(
        String filename,
        @JsonProperty("display_name")
        String displayName,
        List<ScriptParameter> parameters,
        List<String> templates
) {
}
