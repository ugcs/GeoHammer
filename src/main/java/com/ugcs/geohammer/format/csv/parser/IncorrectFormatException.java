package com.ugcs.geohammer.format.csv.parser;

public class IncorrectFormatException extends ParseException {

    public IncorrectFormatException(String value, String format) {
        super("Value '" + value + "' does not match format '" + format + "'");
    }

    public IncorrectFormatException(String message) {
        super(message);
    }
}
