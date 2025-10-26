package com.ugcs.gprvisualizer.app.parsers.exceptions;

public class IncorrectDateFormatException extends ParseException {

    public IncorrectDateFormatException(String message) {
        super(message);
    }

    public IncorrectDateFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
