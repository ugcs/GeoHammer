package com.ugcs.geohammer.model.event;

import java.io.File;
import java.util.List;

public class FileOpenedEvent extends BaseEvent {

    private final List<File> files;
    private final boolean closed;

    public FileOpenedEvent(Object source, List<File> files, boolean closed) {
        super(source);
        this.files = files;
        this.closed = closed;
    }

    public FileOpenedEvent(Object source, List<File> files) {
        this(source, files, false);
    }

    public List<File> getFiles() {
        return files;
    }

    public boolean isClosed() {
        return closed;
    }
}
