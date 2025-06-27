package com.ugcs.gprvisualizer.analytics;

import java.util.Locale;

public class Events {
    public static Event createAppStartedEvent(String appVersion) {
        return new Event(EventType.APP_STARTED, null)
            .withProperty(Event.KEY_APP_VERSION, appVersion)
            .withProperty(Event.KEY_OS_NAME, System.getProperty("os.name"))
            .withProperty(Event.KEY_OS_VERSION, System.getProperty("os.version"))
            .withProperty(Event.KEY_COUNTRY, Locale.getDefault().getDisplayCountry());
    }
}
