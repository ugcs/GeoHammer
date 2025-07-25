package com.ugcs.gprvisualizer.analytics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public class Event {
    private static final Logger log = LoggerFactory.getLogger(Event.class);
    private final EventType eventType;
    private final Data data;
    private final Map<String, Object> properties;
    private final Map<String, Object> userProperties;

    public Event(EventType eventType, @Nullable Data data, @Nullable Map<String, Object> properties, @Nullable Map<String, Object> userProperties) {
        this.eventType = eventType;
        this.data = data;
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

    public Data getData() {
        return data;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public Map<String, Object> getUserProperties() {
        return userProperties;
    }

    public static class Data {
        private final String osName;
        private final String osVersion;
        private final String appVersion;
        private final String ipAddress;

        public Data(String osName, String osVersion, String appVersion, String ipAddress) {
            this.osName = osName;
            this.osVersion = osVersion;
            this.appVersion = appVersion;
            this.ipAddress = ipAddress;
        }

        public String getOsName() {
            return osName;
        }

        public String getOsVersion() {
            return osVersion;
        }

        public String getAppVersion() {
            return appVersion;
        }

        public String getIpAddress() {
            return ipAddress;
        }
    }
}
