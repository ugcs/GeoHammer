package com.ugcs.gprvisualizer.app.file.handler;

import com.github.thecoldwine.sigrun.common.ext.CsvFile;
import com.ugcs.gprvisualizer.analytics.EventSender;
import com.ugcs.gprvisualizer.analytics.EventsFactory;
import com.ugcs.gprvisualizer.app.parcers.csv.CsvParser;
import com.ugcs.gprvisualizer.gpr.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

import static com.ugcs.gprvisualizer.utils.FileTypeUtils.isCsvFile;

@Component
public class CsvFileEventHandler implements FileEventHandler {
    private static final Logger log = LoggerFactory.getLogger(CsvFileEventHandler.class);

    private final EventSender eventSender;
    private final Model model;
    private final EventsFactory eventsFactory;

    public CsvFileEventHandler(EventSender eventSender, Model model, EventsFactory eventsFactory) {
        this.eventSender = eventSender;
        this.model = model;
        this.eventsFactory = eventsFactory;
    }

    @Override
    public boolean canHandle(File file) {
        return isCsvFile(file);
    }

    @Override
    public void handleFileOpened(File file) {
        List<CsvFile> csvFiles = model.getFileManager().getCsvFiles();
        CsvFile csvFile = csvFiles.stream()
                .filter(f -> f.getFile() != null && f.getFile().equals(file))
                .findFirst()
                .orElse(null);
        if (csvFile == null) {
            log.warn("CSV file not found in model: {}", file.getName());
            return;
        }
        CsvParser parser = csvFile.getParser();
        if (parser == null) {
            log.warn("CSV file parser is not initialized for file: {}", file.getName());
            return;
        }
        eventSender.send(eventsFactory.createFileOpenedEvent(parser.getTemplate().getName()));
    }

    @Override
    public void handleFileOpenError(File file, String errorMessage) {
        List<CsvFile> csvFiles = model.getFileManager().getCsvFiles();
        CsvFile csvFile = csvFiles.stream()
                .filter(f -> f.getFile() != null && f.getFile().equals(file))
                .findFirst()
                .orElse(null);
        if (csvFile == null) {
            eventSender.send(eventsFactory.createFileOpenErrorEvent(file.getName(), errorMessage));
            return;
        }
        CsvParser parser = csvFile.getParser();
        if (parser == null) {
            eventSender.send(eventsFactory.createFileOpenErrorEvent(file.getName(), errorMessage));
            return;
        }
        eventSender.send(eventsFactory.createFileOpenErrorEvent(parser.getTemplate().getName(), errorMessage));
    }
}
