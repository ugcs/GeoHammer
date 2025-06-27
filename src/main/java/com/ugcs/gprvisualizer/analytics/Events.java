package com.ugcs.gprvisualizer.analytics;

import java.util.Locale;

public class Events {
    public static Event createAppStartedEvent(String appVersion) {
        return new Event(EventType.APP_STARTED, null, null)
            .withUserProperty(Event.KEY_APP_VERSION, appVersion)
            .withUserProperty(Event.KEY_OS_NAME, System.getProperty("os.name"))
            .withUserProperty(Event.KEY_OS_VERSION, System.getProperty("os.version"))
            .withUserProperty(Event.KEY_COUNTRY, Locale.getDefault().getDisplayCountry());
    }
}
