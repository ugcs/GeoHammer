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

    /**
     * Creates an event for when a file is opened.
     *
     * @param template - the filename or extension of the file that was opened
     * @return an Event object representing the file opened event
     */
    public static Event createFileOpenedEvent(String template) {
        return new Event(EventType.FILE_OPENED, null, null)
            .withProperty("template", template);
    }

    /**
     * Creates an event for when there is an error opening a file.
     *
     * @param template - the filename or extension of the file that failed to open
     * @param errorMessage - The error message  of the exception message
     * @return an Event object representing the file opened error event
     */
    public static Event createFileOpenedErrorEvent(String template, String errorMessage) {
        return new Event(EventType.FILE_OPENED_ERROR, null, null)
            .withProperty("template", template)
            .withProperty("error_message", errorMessage);
    }
}
