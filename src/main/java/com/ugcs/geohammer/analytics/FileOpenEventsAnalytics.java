package com.ugcs.geohammer.analytics;

import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.model.event.FileOpenErrorEvent;
import com.ugcs.geohammer.model.event.FileOpenedEvent;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Templates;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
public class FileOpenEventsAnalytics {

    private final EventSender eventSender;
    private final Model model;
    private final EventsFactory eventsFactory;

    public FileOpenEventsAnalytics(EventSender eventSender, Model model, EventsFactory eventsFactory) {
        this.eventSender = eventSender;
        this.model = model;
        this.eventsFactory = eventsFactory;
    }

    @EventListener
    public void onFileOpened(FileOpenedEvent event) {
        for (File file : Nulls.toEmpty(event.getFiles())) {
            if (file != null) {
                String fileType = getFileType(file);
                eventSender.send(eventsFactory.createFileOpenedEvent(fileType));
            }
        }
    }

    @EventListener
    public void onFileOpenError(FileOpenErrorEvent event) {
        File file = event.getFile();
        if (file == null) {
            return;
        }
        String fileType = getFileType(file);
        String errorMessage = event.getException() != null
                ? event.getException().getMessage()
                : "Unknown error";
        eventSender.send(eventsFactory.createFileOpenErrorEvent(fileType, errorMessage));
    }

    private String getFileType(File file) {
        SgyFile sgyFile = model.getFileManager().getFile(file);
        // use template name when possible
        if (sgyFile != null) {
            return Templates.getTemplateName(sgyFile);
        }
        // or fallback to file name
        return file.getName();
    }
}
