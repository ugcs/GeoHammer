package com.ugcs.gprvisualizer.app.events;

import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.ugcs.gprvisualizer.event.BaseEvent;

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