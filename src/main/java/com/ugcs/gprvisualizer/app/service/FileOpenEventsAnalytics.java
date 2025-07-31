package com.ugcs.gprvisualizer.app.service;

import com.github.thecoldwine.sigrun.common.ext.CsvFile;
import com.ugcs.gprvisualizer.analytics.EventSender;
import com.ugcs.gprvisualizer.analytics.EventsFactory;
import com.ugcs.gprvisualizer.app.parcers.csv.CsvParser;
import com.ugcs.gprvisualizer.event.FileOpenedEvent;
import com.ugcs.gprvisualizer.event.FileOpenErrorEvent;
import com.ugcs.gprvisualizer.gpr.Model;
import com.ugcs.gprvisualizer.utils.FileTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;

@Service
public class FileOpenEventsAnalytics {

    private static final Logger log = LoggerFactory.getLogger(FileOpenEventsAnalytics.class);
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
        for (File file : event.getFiles()) {
            if (file == null || !file.exists()) {
                continue;
            }
            sendFileAnalyticsEvent(file, null);
        }
    }

    @EventListener
    public void onFileOpenError(FileOpenErrorEvent event) {
        File file = event.getFile();
        if (file == null || !file.exists()) {
            log.warn("File does not exist or is null: {}", file);
            return;
        }
        String errorMessage = event.getException() != null
                ? event.getException().getMessage()
                : "Unknown error";
        sendFileAnalyticsEvent(file, errorMessage);
    }

    private void sendFileAnalyticsEvent(File file, @Nullable String errorMessage) {
        String fileType = getTemplateName(file);
        if (fileType == null) {
            log.warn("Unsupported file type: {}", file.getName());
            return;
        }

        if (errorMessage == null) {
            eventSender.send(eventsFactory.createFileOpenedEvent(fileType));
        } else {
            eventSender.send(eventsFactory.createFileOpenErrorEvent(fileType, errorMessage));
        }
    }

    @Nullable
    private String getTemplateName(File file) {
        if (FileTypes.isGprFile(file)) {
            return "sgy";
        } else if (FileTypes.isDztFile(file)) {
            return "dzt";
        } else if (FileTypes.isCsvFile(file)) {
            String csvTemplateName = getCsvTemplateName(file);
            return csvTemplateName != null ? csvTemplateName : file.getName();
        }
        return null;
    }

    @Nullable
    private String getCsvTemplateName(File file) {
        List<CsvFile> csvFiles = model.getFileManager().getCsvFiles();
        CsvFile csvFile = csvFiles.stream()
                .filter(f -> f.getFile() != null && f.getFile().equals(file))
                .findFirst()
                .orElse(null);
        if (csvFile == null) {
            if (FileTypes.isCsvFile(file)) {
                log.warn("CSV file not found in model: {}", file.getName());
            }
            return null;
        }
        CsvParser parser = csvFile.getParser();
        if (parser == null) {
            log.warn("CSV file parser is not initialized for file: {}", file.getName());
            return null;
        }
        return parser.getTemplate().getName();
    }
}
