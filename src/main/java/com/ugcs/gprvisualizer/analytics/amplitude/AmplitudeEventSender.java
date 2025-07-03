package com.ugcs.gprvisualizer.analytics.amplitude;

import com.amplitude.Amplitude;
import com.amplitude.AmplitudeCallbacks;
import com.github.thecoldwine.sigrun.common.ext.CsvFile;
import com.google.common.base.Strings;
import com.ugcs.gprvisualizer.analytics.Event;
import com.ugcs.gprvisualizer.analytics.EventSender;
import com.ugcs.gprvisualizer.analytics.Events;
import com.ugcs.gprvisualizer.app.parcers.csv.CsvParser;
import com.ugcs.gprvisualizer.app.service.UserIdService;
import com.ugcs.gprvisualizer.event.FileOpenErrorEvent;
import com.ugcs.gprvisualizer.event.FileOpenedEvent;
import com.ugcs.gprvisualizer.gpr.Model;
import javafx.util.Pair;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.ugcs.gprvisualizer.utils.FileTypeUtils.*;

public class AmplitudeEventSender implements EventSender {

    private static final Logger log = LoggerFactory.getLogger(AmplitudeEventSender.class);

    private static final int HTTP_STATUS_OK = 200;
    final Amplitude amplitude;

    @Autowired
    private UserIdService userIdService;

    @Autowired
    private Model model;

    public AmplitudeEventSender(String apiKey, Boolean isEnabled) {
        if (!isEnabled) {
            log.info("Amplitude integration is disabled by configuration");
            amplitude = null;
            return;
        }
        if (!Strings.isNullOrEmpty(apiKey)) {
            amplitude = Amplitude.getInstance();
            amplitude.init(apiKey);
        } else {
            log.info("Amplitude integration disabled, no API key provided in config");
            amplitude = null;
        }
    }

    private com.amplitude.Event toAmplitudeEvent(Event event) {
        if (amplitude == null) {
            throw new IllegalStateException("Amplitude is not initialized, API key is missing");
        }
        com.amplitude.Event amplitudeEvent = new com.amplitude.Event(
                event.getEventType().getCode(),
                userIdService.getOrCreateUserId()
        );
        amplitudeEvent.userProperties = buildUserProperties(event);
        amplitudeEvent.eventProperties = buildEventProperties(event);
        return amplitudeEvent;
    }

    private JSONObject buildUserProperties(Event event) {
        JSONObject userProperties = new JSONObject();
        event.getUserProperties().forEach((originalKey, originalValue) -> {
            Pair<String, Object> property = mapUserProperty(originalKey, originalValue);
            String key = property.getKey();
            Object value = property.getValue();
            if (value != null) {
                userProperties.put(key, value.toString());
            } else {
                log.warn("User property '{}' is null, skipping", key);
            }
        });
        return userProperties;
    }

    private JSONObject buildEventProperties(Event event) {
        JSONObject eventProperties = new JSONObject();
        event.getProperties().forEach((key, value) -> {
            if (value != null) {
                eventProperties.put(key, value.toString());
            } else {
                log.warn("Property '{}' is null, skipping", key);
            }
        });
        return eventProperties;
    }

    private Pair<String, Object> mapUserProperty(String key, Object value) {
        return switch (key) {
            case Event.KEY_APP_VERSION -> new Pair<>("version", value);
            case Event.KEY_OS_NAME -> new Pair<>("os_name", value);
            case Event.KEY_OS_VERSION -> new Pair<>("os_version", value);
            case Event.KEY_COUNTRY -> new Pair<>("country", value);
            default -> new Pair<>(key, value);
        };
    }

    @Override
    public void send(Event event) {
        if (amplitude == null) {
            log.warn("Amplitude is disabled, cannot send event: {}", event.getEventType());
            return;
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        com.amplitude.Event amplitudeEvent = toAmplitudeEvent(event);
        log.trace("Sending event to Amplitude: {}", amplitudeEvent);
        amplitude.logEvent(amplitudeEvent, new AmplitudeCallbacks() {
            @Override
            public void onLogEventServerResponse(com.amplitude.Event event, int status, String message) {
                if (status == HTTP_STATUS_OK) {
                    future.complete(null);
                    log.trace("Amplitude event logged successfully: {}", event);
                } else {
                    String errorMessage = String.format("Amplitude error %s: %s", status, message);
                    future.completeExceptionally(new Exception(errorMessage));
                    log.error(errorMessage);
                }
            }
        });
    }

    @EventListener
    void listenFileOpenedEvent(FileOpenedEvent fileOpenedEvent) {
        if (amplitude == null) {
            log.warn("Amplitude is disabled, cannot send file opened event");
            return;
        }
        for (File file : fileOpenedEvent.getFiles()) {
            if (file == null || !file.exists()) {
                log.warn("File opened event for non-existing file: {}", file);
                continue;
            }
            handleFileOpenedEvent(file);
        }
    }

    private void handleFileOpenedEvent(File file) {
        if (isCsvFile(file)) {
            handleCsvFileOpened(file);
        } else if (isSgyFile(file)) {
            send(Events.createFileOpenedEvent("sgy"));
        } else if (isDztFile(file)) {
            send(Events.createFileOpenedEvent("dzt"));
        }
    }

    private void handleCsvFileOpened(File file) {
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
        send(Events.createFileOpenedEvent(parser.getTemplate().getName()));
    }

    @EventListener
    void listenFileOpenedErrorEvent(FileOpenErrorEvent fileOpenErrorEvent) {
        if (amplitude == null) {
            log.warn("Amplitude is disabled, cannot send file open error event");
            return;
        }
        File file = fileOpenErrorEvent.getFile();
        if (file == null || !file.exists()) {
            log.warn("File open error event for non-existing file: {}", file);
            return;
        }
        String errorMessage = fileOpenErrorEvent.getException() != null
                ? fileOpenErrorEvent.getException().getMessage()
                : "Unknown error";
        handleFileOpenErrorEvent(file, errorMessage);
    }

    private void handleFileOpenErrorEvent(File file, String errorMessage) {
        if (isCsvFile(file)) {
            handleCsvFileOpenError(file, errorMessage);
        } else if (isSgyFile(file)) {
            send(Events.createFileOpenedErrorEvent("sgy", errorMessage));
        } else if (isDztFile(file)) {
            send(Events.createFileOpenedErrorEvent("dzt", errorMessage));
        }
    }

    private void handleCsvFileOpenError(File file, String errorMessage) {
        List<CsvFile> csvFiles = model.getFileManager().getCsvFiles();
        CsvFile csvFile = csvFiles.stream()
                .filter(f -> f.getFile() != null && f.getFile().equals(file))
                .findFirst()
                .orElse(null);
        if (csvFile == null) {
            send(Events.createFileOpenedErrorEvent(file.getName(), errorMessage));
            return;
        }
        CsvParser parser = csvFile.getParser();
        if (parser == null) {
            send(Events.createFileOpenedErrorEvent(file.getName(), errorMessage));
            return;
        }
        send(Events.createFileOpenedErrorEvent(parser.getTemplate().getName(), errorMessage));
    }

    // Created to allow testing with a mock UserIdService
    void setUserIdService(UserIdService userIdService) {
        this.userIdService = userIdService;
    }

    // Created to allow testing with a mock UserIdService
    void setModel(Model model) {
        this.model = model;
    }

    @Override
    public void shutdown() {
        if (amplitude != null) {
            amplitude.flushEvents();
            try {
                amplitude.shutdown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
