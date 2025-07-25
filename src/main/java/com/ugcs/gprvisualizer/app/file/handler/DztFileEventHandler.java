package com.ugcs.gprvisualizer.app.file.handler;

import com.ugcs.gprvisualizer.analytics.EventSender;
import com.ugcs.gprvisualizer.analytics.EventsFactory;
import org.springframework.stereotype.Component;

import java.io.File;

import static com.ugcs.gprvisualizer.utils.FileTypeUtils.isDztFile;

@Component
public class DztFileEventHandler implements FileEventHandler {

    private final EventSender eventSender;
    private final EventsFactory eventsFactory;

    public DztFileEventHandler(EventSender eventSender, EventsFactory eventsFactory) {
        this.eventSender = eventSender;
        this.eventsFactory = eventsFactory;
    }

    @Override
    public boolean canHandle(File file) {
        return isDztFile(file);
    }

    @Override
    public void handleFileOpened(File file) {
        eventSender.send(eventsFactory.createFileOpenedEvent("dzt"));
    }

    @Override
    public void handleFileOpenError(File file, String errorMessage) {
        eventSender.send(eventsFactory.createFileOpenErrorEvent("dzt", errorMessage));
    }
}
