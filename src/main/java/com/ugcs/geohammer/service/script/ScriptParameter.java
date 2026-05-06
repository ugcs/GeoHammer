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
        List<String> enumValues,
        @JsonProperty("min")
        Double min,
        @JsonProperty("max")
        Double max
) {
    public void validate() {
		switch (type) {
			case ENUM -> validateEnum();
			case INTEGER -> validateInteger();
			default -> { }
		}
    }

	private void validateEnum() {
		if (enumValues == null || enumValues.isEmpty()) {
			throw new IllegalArgumentException(
					"Parameter '" + name + "' has type ENUM but 'enum_values' is missing or empty");
		}
	}

	private void validateInteger() {
		if (min != null && max != null && min > max) {
			throw new IllegalArgumentException(
					"Parameter '" + name + "': min (" + formatBound(min) + ") must not exceed max (" + formatBound(max) + ")");
		}
	}

	public void validateValue(String value) {
		if (value == null || value.isEmpty()) {
			return;
		}
		switch (type) {
			case INTEGER -> {
				int parsed;
				try {
					parsed = Integer.parseInt(value.trim());
				} catch (NumberFormatException e) {
					throw new IllegalArgumentException(
							"Parameter '" + displayName + "': '" + value + "' is not a valid integer");
				}
				if (min != null && parsed < min) {
					throw new IllegalArgumentException(
							"Parameter '" + displayName + "': value " + parsed + " is below minimum " + formatBound(min));
				}
				if (max != null && parsed > max) {
					throw new IllegalArgumentException(
							"Parameter '" + displayName + "': value " + parsed + " exceeds maximum " + formatBound(max));
				}
			}
			case DOUBLE -> {
				double parsed;
				try {
					parsed = Double.parseDouble(value.trim());
				} catch (NumberFormatException e) {
					throw new IllegalArgumentException(
							"Parameter '" + displayName + "': '" + value + "' is not a valid number");
				}
				if (min != null && parsed < min) {
					throw new IllegalArgumentException(
							"Parameter '" + displayName + "': value " + parsed + " is below minimum " + formatBound(min));
				}
				if (max != null && parsed > max) {
					throw new IllegalArgumentException(
							"Parameter '" + displayName + "': value " + parsed + " exceeds maximum " + formatBound(max));
				}
			}
		}
	}

	private static String formatBound(double value) {
		return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
	}

    public enum ParameterType {
        STRING, INTEGER, DOUBLE, BOOLEAN, FILE_PATH, FOLDER_PATH, COLUMN_NAME, ENUM
    }
}
