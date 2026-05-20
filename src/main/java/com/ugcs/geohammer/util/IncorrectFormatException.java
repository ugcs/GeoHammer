package com.ugcs.geohammer.util;

public class IncorrectFormatException extends RuntimeException {

    public IncorrectFormatException(String value, String format) {
        super("Value '" + value + "' does not match format '" + format + "'");
    }

    public IncorrectFormatException(String message) {
        super(message);
    }
}
