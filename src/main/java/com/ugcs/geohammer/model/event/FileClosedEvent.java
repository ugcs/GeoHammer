package com.ugcs.geohammer.model.event;

import com.ugcs.geohammer.format.SgyFile;

public class FileClosedEvent extends BaseEvent {

    private final SgyFile file;

    public FileClosedEvent(Object source, SgyFile file) {
        super(source);
        this.file = file;
    }

    public SgyFile getFile() {
        return file;
    }
}