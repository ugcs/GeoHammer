package com.ugcs.geohammer.analytics;

import com.ugcs.geohammer.model.event.FilterType;
import com.ugcs.geohammer.release.Version;
import org.springframework.stereotype.Component;

@Component
public class EventsFactory {
    public Event createAppStartedEvent(Version appVersion) {
        Event.ClientProperties clientProperties = new Event.ClientProperties(
                System.getProperty("os.name"),
                System.getProperty("os.version"),
                Version.toString(appVersion)
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

    public Event createLicenseValidatedEvent(String name, String expiresAt) {
        return new Event(EventType.LICENSE_VALIDATED, null, null, null)
                .withProperty("name", name)
                .withProperty("expires_at", expiresAt);
    }

    public Event createScriptExecutionStartedEvent(String scriptName, String pythonVersion) {
        return new Event(EventType.SCRIPT_EXECUTION_STARTED, null, null, null)
                .withProperty("script_name", scriptName)
                .withProperty("python_version", pythonVersion);
    }

    public Event createFilterAppliedEvent(FilterType filterType, String template) {
        return new Event(EventType.FILTER_APPLIED, null, null, null)
                .withProperty("filter", filterType.getCode())
                .withProperty("template", template);
    }

    public Event createMcpSessionInitEvent(String clientName, String clientVersion) {
        return new Event(EventType.MCP_SESSION_INIT, null, null, null)
                .withProperty("client_name", clientName)
                .withProperty("client_version", clientVersion);
    }

    public Event createMcpScriptCreatedEvent(String scriptName) {
        return new Event(EventType.MCP_SCRIPT_CREATED, null, null, null)
                .withProperty("script_name", scriptName);
    }
}
