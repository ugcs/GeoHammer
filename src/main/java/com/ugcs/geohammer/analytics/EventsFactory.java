package com.ugcs.geohammer.analytics;

import com.ugcs.geohammer.analytics.amplitude.PublicIpAddressProvider;
import org.springframework.stereotype.Component;

@Component
public class EventsFactory {
    private final PublicIpAddressProvider publicIpAddressProvider;

    public EventsFactory(PublicIpAddressProvider publicIpAddressProvider) {
        this.publicIpAddressProvider = publicIpAddressProvider;
    }

    public Event createAppStartedEvent(String appVersion) {
        Event.ClientProperties clientProperties = new Event.ClientProperties(
                System.getProperty("os.name"),
                System.getProperty("os.version"),
                appVersion,
                publicIpAddressProvider.getPublicIpAddress()
        );
        return new Event(EventType.APP_STARTED, clientProperties, null, null);
    }

    public Event createFileOpenedEvent(String template) {
        return new Event(EventType.FILE_OPENED, null, null, null)
                .withProperty("template", template);
    }

    public Event createFileOpenErrorEvent(String template, String errorMessage) {
        return new Event(EventType.FILE_OPEN_ERROR, null, null, null)
                .withProperty("template", template)
                .withProperty("error_message", errorMessage);
    }
}