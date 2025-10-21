package com.ugcs.gprvisualizer.app.parsers.exceptions;

public class UnknownNmeaTypeException extends RuntimeException {

    public UnknownNmeaTypeException(String message) {
        super(message);
    }
}