package com.ugcs.gprvisualizer.app.events;

import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.ugcs.gprvisualizer.app.auxcontrol.RemoveFileButton;
import com.ugcs.gprvisualizer.event.BaseEvent;
import com.ugcs.gprvisualizer.event.FileOpenedEvent;

import java.io.File;
import java.util.List;

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