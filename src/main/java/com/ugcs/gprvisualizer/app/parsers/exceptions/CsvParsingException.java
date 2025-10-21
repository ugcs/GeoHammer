package com.ugcs.gprvisualizer.app.parsers.exceptions;

import java.io.File;

public class CsvParsingException extends RuntimeException {

    private final File file;

    public CsvParsingException(File file, String message) {
        super(message);
        this.file = file;
    }

    public File getFile() {
        return file;
    }
}
