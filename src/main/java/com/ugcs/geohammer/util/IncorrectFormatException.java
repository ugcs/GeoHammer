package com.ugcs.geohammer.util;

public class IncorrectFormatException extends RuntimeException {

	private final String value;

	private final String format;

    public IncorrectFormatException(String value, String format) {
		this.value = value;
		this.format = format;
    }

	@Override
	public String getMessage() {
		return "Value '" + value + "' does not match format '" + format + "'";
	}

	public String getValue() {
		return value;
	}

	public String getFormat() {
		return format;
	}
}
