package com.ugcs.gprvisualizer.analytics;

import com.ugcs.gprvisualizer.geolocation.IpProvider;
import org.springframework.stereotype.Component;

@Component
public class EventsFactory {
    private final IpProvider ipProvider;

    public EventsFactory(IpProvider ipProvider) {
        this.ipProvider = ipProvider;
    }

    public Event createAppStartedEvent(String appVersion) {
        Event.Data data = new Event.Data(
                System.getProperty("os.name"),
                System.getProperty("os.version"),
                appVersion,
                ipProvider.getIpAddress()
        );
        return new Event(EventType.APP_STARTED, data, null, null);
    }

    public Event createFileOpenedEvent(String template) {
        return new Event(EventType.FILE_OPENED, null, null, null)
                .withProperty("template", template);
    }

    public Event createFileOpenedErrorEvent(String template, String errorMessage) {
        return new Event(EventType.FILE_OPENED_ERROR, null, null, null)
                .withProperty("template", template)
                .withProperty("error_message", errorMessage);
    }
}