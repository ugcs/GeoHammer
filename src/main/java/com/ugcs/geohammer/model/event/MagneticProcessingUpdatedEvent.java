package com.ugcs.geohammer.model.event;

import com.ugcs.geohammer.format.SgyFile;

public class MagneticProcessingUpdatedEvent extends BaseEvent {

    private final SgyFile file;

    public MagneticProcessingUpdatedEvent(Object source, SgyFile file) {
        super(source);
        this.file = file;
    }

    public SgyFile getFile() {
        return file;
    }
}
