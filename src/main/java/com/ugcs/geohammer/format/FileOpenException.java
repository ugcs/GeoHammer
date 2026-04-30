package com.ugcs.geohammer.format;

import java.io.File;
import java.io.IOException;

public class FileOpenException extends IOException {

    private final File file;

    public FileOpenException(File file, Throwable cause) {
        super(cause != null ? cause.getMessage() : null, cause);
        this.file = file;
    }

    public File getFile() {
        return file;
    }
}
