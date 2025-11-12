package com.ugcs.geohammer.model.event;

import com.ugcs.geohammer.format.SgyFile;

public class FileClosedEvent extends BaseEvent {

    private final SgyFile sgyFile;

    public FileClosedEvent(Object source, SgyFile sgyFile) {
        super(source);
        this.sgyFile = sgyFile;
    }

    public SgyFile getSgyFile() {
        return sgyFile;
    }
}