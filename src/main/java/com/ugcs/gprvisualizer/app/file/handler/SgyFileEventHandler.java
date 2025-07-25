package com.ugcs.gprvisualizer.app.file.handler;

import com.ugcs.gprvisualizer.analytics.EventSender;
import com.ugcs.gprvisualizer.analytics.EventsFactory;
import org.springframework.stereotype.Component;

import java.io.File;

import static com.ugcs.gprvisualizer.utils.FileTypeUtils.isSgyFile;

@Component
public class SgyFileEventHandler implements FileEventHandler {

    private final EventSender eventSender;
    private final EventsFactory eventsFactory;

    public SgyFileEventHandler(EventSender eventSender, EventsFactory eventsFactory) {
        this.eventSender = eventSender;
        this.eventsFactory = eventsFactory;
    }

    @Override
    public boolean canHandle(File file) {
        return isSgyFile(file);
    }

    @Override
    public void handleFileOpened(File file) {
        eventSender.send(eventsFactory.createFileOpenedEvent("sgy"));
    }

    @Override
    public void handleFileOpenError(File file, String errorMessage) {
        eventSender.send(eventsFactory.createFileOpenErrorEvent("sgy", errorMessage));
    }
}
