package com.ugcs.geohammer.format.csv.parser;

public class IncorrectDateFormatException extends ParseException {

    public IncorrectDateFormatException(String message) {
        super(message);
    }

    public IncorrectDateFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
