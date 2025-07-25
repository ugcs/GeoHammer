package com.ugcs.gprvisualizer.analytics.amplitude;

import com.amplitude.Amplitude;
import com.amplitude.AmplitudeCallbacks;
import com.ugcs.gprvisualizer.analytics.Event;
import com.ugcs.gprvisualizer.analytics.EventType;
import com.ugcs.gprvisualizer.app.service.UserIdService;
import com.ugcs.gprvisualizer.gpr.Model;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AmplitudeEventSenderTest {

    private static final String VALID_API_KEY = "test-key";
    private Amplitude mockAmplitude;
    private UserIdService mockUserIdService;

    @BeforeEach
    void setUp() {
        mockAmplitude = mock(Amplitude.class);
        mockUserIdService = mock(UserIdService.class);
        mock(Model.class);
    }

    @Test
    void testAmplitudeInitializedWithApiKey() {
        try (MockedStatic<Amplitude> amplitudeStatic = mockStatic(Amplitude.class)) {
            amplitudeStatic.when(Amplitude::getInstance).thenReturn(mockAmplitude);
            new AmplitudeEventSender(VALID_API_KEY, true);
            amplitudeStatic.verify(Amplitude::getInstance);
            verify(mockAmplitude).init(VALID_API_KEY);
        }
    }

    @Test
    void testAmplitudeNotInitializedWithEmptyApiKey() {
        try (MockedStatic<Amplitude> amplitudeStatic = mockStatic(Amplitude.class)) {
            AmplitudeEventSender sender = new AmplitudeEventSender("", true);
            sender.setUserIdService(mockUserIdService);

            Event event = createMockEvent();
            sender.send(event);

            amplitudeStatic.verify(Amplitude::getInstance, never());
        }
    }

    @Test
    void testAmplitudeNotInitializedWithNullApiKey() {
        try (MockedStatic<Amplitude> amplitudeStatic = mockStatic(Amplitude.class)) {
            AmplitudeEventSender sender = new AmplitudeEventSender(null, true);
            sender.setUserIdService(mockUserIdService);

            Event event = createMockEvent();
            sender.send(event);

            amplitudeStatic.verify(Amplitude::getInstance, never());
        }
    }

    @Test
    void testAmplitudeDisabledByConfig() {
        try (MockedStatic<Amplitude> amplitudeStatic = mockStatic(Amplitude.class)) {
            AmplitudeEventSender sender = new AmplitudeEventSender(VALID_API_KEY, false);
            sender.setUserIdService(mockUserIdService);

            Event event = createMockEvent();
            sender.send(event);

            amplitudeStatic.verify(Amplitude::getInstance, never());
        }
    }

    @Test
    void testSendEventSuccess() {
        try (MockedStatic<Amplitude> amplitudeStatic = mockStatic(Amplitude.class)) {
            amplitudeStatic.when(Amplitude::getInstance).thenReturn(mockAmplitude);
            AmplitudeEventSender sender = new AmplitudeEventSender(VALID_API_KEY, true);
            sender.setUserIdService(mockUserIdService);

            Event event = createMockEvent();
            when(mockUserIdService.getOrCreateUserId()).thenReturn("user123");

            ArgumentCaptor<com.amplitude.Event> eventCaptor = ArgumentCaptor.forClass(com.amplitude.Event.class);
            ArgumentCaptor<AmplitudeCallbacks> callbackCaptor = ArgumentCaptor.forClass(AmplitudeCallbacks.class);

            sender.send(event);

            verify(mockAmplitude).logEvent(eventCaptor.capture(), callbackCaptor.capture());

            com.amplitude.Event capturedEvent = eventCaptor.getValue();
            assertEquals("test_event", capturedEvent.eventType);
            assertEquals("user123", capturedEvent.userId);

            // Simulate successful callback
            AmplitudeCallbacks callbacks = callbackCaptor.getValue();
            callbacks.onLogEventServerResponse(capturedEvent, 200, "OK");
        }
    }

    @Test
    void testSendEventWithEventData() {
        try (MockedStatic<Amplitude> amplitudeStatic = mockStatic(Amplitude.class)) {
            amplitudeStatic.when(Amplitude::getInstance).thenReturn(mockAmplitude);
            AmplitudeEventSender sender = new AmplitudeEventSender(VALID_API_KEY, true);
            sender.setUserIdService(mockUserIdService);

            Event event = createMockEventWithData();
            when(mockUserIdService.getOrCreateUserId()).thenReturn("user123");

            ArgumentCaptor<com.amplitude.Event> eventCaptor = ArgumentCaptor.forClass(com.amplitude.Event.class);

            sender.send(event);

            verify(mockAmplitude).logEvent(eventCaptor.capture(), any(AmplitudeCallbacks.class));

            com.amplitude.Event capturedEvent = eventCaptor.getValue();
            assertEquals("test_event", capturedEvent.eventType);
            assertEquals("macOS", capturedEvent.osName);
            assertEquals("14.0", capturedEvent.osVersion);
            assertEquals("1.0.0", capturedEvent.appVersion);
        }
    }

    @Test
    void testSendEventWithProperties() {
        try (MockedStatic<Amplitude> amplitudeStatic = mockStatic(Amplitude.class)) {
            amplitudeStatic.when(Amplitude::getInstance).thenReturn(mockAmplitude);
            AmplitudeEventSender sender = new AmplitudeEventSender(VALID_API_KEY, true);
            sender.setUserIdService(mockUserIdService);

            Event event = createMockEventWithProperties();
            when(mockUserIdService.getOrCreateUserId()).thenReturn("user123");

            ArgumentCaptor<com.amplitude.Event> eventCaptor = ArgumentCaptor.forClass(com.amplitude.Event.class);

            sender.send(event);

            verify(mockAmplitude).logEvent(eventCaptor.capture(), any(AmplitudeCallbacks.class));

            com.amplitude.Event capturedEvent = eventCaptor.getValue();
            assertNotNull(capturedEvent.eventProperties);
            assertNotNull(capturedEvent.userProperties);
        }
    }

    @Test
    void testSendEventWhenAmplitudeIsNull() {
        AmplitudeEventSender sender = new AmplitudeEventSender("", true);
        sender.setUserIdService(mockUserIdService);

        Event event = createMockEvent();

        // Should not throw exception, just log warning
        assertDoesNotThrow(() -> sender.send(event));
    }

    @Test
    void testShutdownWithAmplitude() throws InterruptedException {
        try (MockedStatic<Amplitude> amplitudeStatic = mockStatic(Amplitude.class)) {
            amplitudeStatic.when(Amplitude::getInstance).thenReturn(mockAmplitude);
            AmplitudeEventSender sender = new AmplitudeEventSender(VALID_API_KEY, true);

            sender.shutdown();

            verify(mockAmplitude).flushEvents();
            verify(mockAmplitude).shutdown();
        }
    }

    @Test
    void testShutdownWithoutAmplitude() {
        AmplitudeEventSender sender = new AmplitudeEventSender("", true);

        // Should not throw exception
        assertDoesNotThrow(sender::shutdown);
    }

    @Test
    void testShutdownHandlesInterruptedException() throws InterruptedException {
        try (MockedStatic<Amplitude> amplitudeStatic = mockStatic(Amplitude.class)) {
            amplitudeStatic.when(Amplitude::getInstance).thenReturn(mockAmplitude);
            doThrow(new InterruptedException()).when(mockAmplitude).shutdown();

            AmplitudeEventSender sender = new AmplitudeEventSender(VALID_API_KEY, true);

            sender.shutdown();

            verify(mockAmplitude).flushEvents();
            verify(mockAmplitude).shutdown();
            assertTrue(Thread.currentThread().isInterrupted());
        }
    }

    private Event createMockEvent() {
        Event event = mock(Event.class);
        EventType eventType = mock(EventType.class);
        when(event.getEventType()).thenReturn(eventType);
        when(eventType.getCode()).thenReturn("test_event");
        when(event.getProperties()).thenReturn(Collections.emptyMap());
        when(event.getUserProperties()).thenReturn(Collections.emptyMap());
        when(event.getData()).thenReturn(null);
        return event;
    }

    private Event createMockEventWithData() {
        Event event = createMockEvent();
        Event.Data eventData = mock(Event.Data.class);
        when(eventData.getOsName()).thenReturn("macOS");
        when(eventData.getOsVersion()).thenReturn("14.0");
        when(eventData.getAppVersion()).thenReturn("1.0.0");
        when(eventData.getIpAddress()).thenReturn("127.0.0.1");
        when(event.getData()).thenReturn(eventData);
        return event;
    }

    private Event createMockEventWithProperties() {
        Event event = createMockEvent();
        Map<String, Object> properties = Map.of("key1", "value1", "key2", 123);
        Map<String, Object> userProperties = Map.of("userKey1", "userValue1");
        when(event.getProperties()).thenReturn(properties);
        when(event.getUserProperties()).thenReturn(userProperties);
        return event;
    }
}