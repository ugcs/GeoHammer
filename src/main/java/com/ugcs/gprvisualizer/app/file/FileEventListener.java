package com.ugcs.gprvisualizer.app.file;

import com.ugcs.gprvisualizer.app.file.handler.FileEventHandler;
import com.ugcs.gprvisualizer.event.FileOpenErrorEvent;
import com.ugcs.gprvisualizer.event.FileOpenedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

@Component
public class FileEventListener {
    private final List<FileEventHandler> fileEventHandlers;

    public FileEventListener(List<FileEventHandler> fileEventHandlers) {
        this.fileEventHandlers = fileEventHandlers;
    }

    @EventListener
    public void onFileOpened(FileOpenedEvent event) {
        for (File file : event.getFiles()) {
            if (file == null || !file.exists()) {
                continue;
            }
            fileEventHandlers.stream()
                    .filter(handler -> handler.canHandle(file))
                    .findFirst()
                    .ifPresent(handler -> handler.handleFileOpened(file));
        }
    }

    @EventListener
    public void onFileOpenError(FileOpenErrorEvent event) {
        File file = event.getFile();
        if (file == null || !file.exists()) {
            return;
        }
        String errorMessage = event.getException() != null
                ? event.getException().getMessage()
                : "Unknown error";

        fileEventHandlers.stream()
                .filter(handler -> handler.canHandle(file))
                .findFirst()
                .ifPresent(handler -> handler.handleFileOpenError(file, errorMessage));
    }
}
