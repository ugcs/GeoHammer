package com.ugcs.gprvisualizer.event;

import java.io.File;

public class FileOpenErrorEvent extends BaseEvent {
    private final File file;

    private final Exception exception;

    public FileOpenErrorEvent(Object source, File file, Exception exception) {
        super(source);
        this.file = file;
        this.exception = exception;
    }

    public File getFile() {
        return file;
    }

    public Exception getException() {
        return exception;
    }
}
