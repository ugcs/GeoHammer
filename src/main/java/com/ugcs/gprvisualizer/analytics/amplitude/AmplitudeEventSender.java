package com.ugcs.gprvisualizer.analytics.amplitude;

import com.amplitude.Amplitude;
import com.amplitude.AmplitudeCallbacks;
import com.google.common.base.Strings;
import com.ugcs.gprvisualizer.analytics.Event;
import com.ugcs.gprvisualizer.analytics.EventSender;
import com.ugcs.gprvisualizer.app.service.UserIdService;
import javafx.util.Pair;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.CompletableFuture;

public class AmplitudeEventSender implements EventSender {

    private static final Logger log = LoggerFactory.getLogger(AmplitudeEventSender.class);

    private static final int HTTP_STATUS_OK = 200;
    private final Amplitude amplitude;

    @Autowired
    private UserIdService userIdService;

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
        com.amplitude.Event amplitudeEvent = new com.amplitude.Event(event.getEventType().getCode(), userIdService.getOrCreateUserId());
        JSONObject eventProperties = new JSONObject();
        event.getProperties().forEach((originalKey, originalValue) -> {
            Pair<String, Object> property = toAmplitudeProperty(originalKey, originalValue);
            String key = property.getKey();
            Object value = property.getValue();
            if (value != null) {
                eventProperties.put(key, value.toString());
            } else {
                log.warn("Property '{}' is null, skipping", key);
            }
        });
        if (!eventProperties.isEmpty()) {
            amplitudeEvent.userProperties = eventProperties;
        }
        return amplitudeEvent;
    }

    private Pair<String, Object> toAmplitudeProperty(String key, Object value) {
       switch (key) {
            case Event.KEY_APP_VERSION -> {
                return new Pair<>("version", value);
            }
            case Event.KEY_OS_NAME -> {
                return new Pair<>("os_name", value);
            }
            case Event.KEY_OS_VERSION -> {
                return new Pair<>("os_version", value);
            }
            case Event.KEY_COUNTRY -> {
                return new Pair<>("country", value);
            }
            default -> {
                return new Pair<>(key, value);
            }
       }
    }

    @Override
    public void send(Event event) {
        if (amplitude == null) {
           log.warn("Amplitude is not initialized, cannot send event: {}", event.getEventType());
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

    public void setUserIdService(UserIdService userIdService) {
        this.userIdService = userIdService;
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
