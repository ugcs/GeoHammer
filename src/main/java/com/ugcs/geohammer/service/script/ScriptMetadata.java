package com.ugcs.geohammer.service.script;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record ScriptMetadata(
        String filename,
        @JsonProperty("display_name")
        String displayName,
        List<ScriptParameter> parameters,
        List<String> templates
) {
	public void validateRequiredParameters(Map<String, String> parameters) throws ScriptValidationException {
		List<String> missing = parameters().stream()
				.filter(ScriptParameter::required)
				.filter(p -> !parameters.containsKey(p.name()) || parameters.get(p.name()).isEmpty())
				.map(ScriptParameter::displayName)
				.toList();
		if (!missing.isEmpty()) {
			throw new ScriptValidationException("Missing required parameters: "
					+ String.join(", ", missing));
		}
	}

}
