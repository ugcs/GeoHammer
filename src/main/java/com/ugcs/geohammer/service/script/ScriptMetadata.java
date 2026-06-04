package com.ugcs.geohammer.service.script;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ugcs.geohammer.util.OperatingSystemUtils;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public record ScriptMetadata(
        String filename,
        @JsonProperty("display_name")
        String displayName,
        List<ScriptParameter> parameters,
        List<String> templates,
        @JsonProperty("operating_systems")
        @Nullable List<String> operatingSystems
) {
	public boolean supportsCurrentOs() {
		if (operatingSystems == null || operatingSystems.isEmpty()) {
			return true;
		}
		if (OperatingSystemUtils.isWindows()) {
			return operatingSystems.contains("win");
		}
		if (OperatingSystemUtils.isMac()) {
			return operatingSystems.contains("mac");
		}
		if (OperatingSystemUtils.isLinux()) {
			return operatingSystems.contains("linux");
		}
		return false;
	}

	public boolean isBooleanParameter(String name) {
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
