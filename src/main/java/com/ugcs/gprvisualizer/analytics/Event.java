package com.ugcs.gprvisualizer.analytics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public class Event {
    private static final Logger log = LoggerFactory.getLogger(Event.class);
    private final EventType eventType;
    private final Map<String, Object> properties;
    private final Map<String, Object> userProperties;

    public static final String KEY_APP_VERSION = "appVersion";
    public static final String KEY_OS_NAME = "osName";
    public static final String KEY_OS_VERSION = "osVersion";
    public static final String KEY_COUNTRY = "country";

    public Event(EventType eventType, @Nullable Map<String, Object> properties, @Nullable Map<String, Object> userProperties) {
        this.eventType = eventType;
        this.properties = properties != null ? properties : new HashMap<>();
        this.userProperties = userProperties != null ? userProperties : new HashMap<>();
    }

    public Event withProperty(String key, Object value) {
        if (value == null) {
            log.warn("Setting property '{}' to null, removing it from properties", key);
            properties.remove(key);
        } else {
            properties.put(key, value);
        }
        return this;
    }

    public Event withUserProperty(String key, Object value) {
        if (value == null) {
            log.warn("Setting user property '{}' to null, removing it from user properties", key);
            userProperties.remove(key);
        } else {
            userProperties.put(key, value);
        }
        return this;
    }

    public EventType getEventType() {
        return eventType;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public Map<String, Object> getUserProperties() {
        return userProperties;
    }
}