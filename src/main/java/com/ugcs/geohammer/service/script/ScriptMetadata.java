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
	public void appendArgs(List<String> command, Map<String, String> values) {
		for (Map.Entry<String, String> entry : values.entrySet()) {
			String name = entry.getKey();
			String value = entry.getValue();
			if (isBooleanParameter(name)) {
				if (Boolean.parseBoolean(value)) {
					command.add("--" + name);
				}
			} else {
				command.add("--" + name);
				command.add(value);
			}
		}
	}

	private boolean isBooleanParameter(String name) {
		for (ScriptParameter param : parameters()) {
			if (param.name().equals(name)) {
				return param.type() == ScriptParameter.ParameterType.BOOLEAN;
			}
		}
		return false;
	}

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
		for (ScriptParameter param : parameters()) {
			String value = parameters.get(param.name());
			if (value != null && !value.isEmpty()) {
				try {
					param.validateValue(value);
				} catch (IllegalArgumentException e) {
					throw new ScriptValidationException(e.getMessage());
				}
			}
		}
	}

}
