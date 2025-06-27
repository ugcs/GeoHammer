package com.ugcs.gprvisualizer.analytics.amplitude;

import com.amplitude.Amplitude;
import com.amplitude.AmplitudeCallbacks;
import com.ugcs.gprvisualizer.analytics.Event;
import com.ugcs.gprvisualizer.analytics.EventType;
import com.ugcs.gprvisualizer.app.service.UserIdService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.Collections;

import static org.mockito.Mockito.*;

class AmplitudeEventSenderTest {

    private static final String VALID_API_KEY = "test-key";
    private Amplitude mockAmplitude;
    private UserIdService mockUserIdService;

    @BeforeEach
    void setUp() {
        mockAmplitude = mock(Amplitude.class);
        mockUserIdService = mock(UserIdService.class);
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
    void testSendEventSuccess() {
        try (MockedStatic<Amplitude> amplitudeStatic = mockStatic(Amplitude.class)) {
            amplitudeStatic.when(Amplitude::getInstance).thenReturn(mockAmplitude);
            AmplitudeEventSender sender = new AmplitudeEventSender(VALID_API_KEY, true);
            sender.setUserIdService(mockUserIdService);

            Event event = mock(Event.class);
            EventType eventType = mock(EventType.class);
            when(event.getEventType()).thenReturn(eventType);
            when(eventType.getCode()).thenReturn("test_event");
            when(event.getProperties()).thenReturn(Collections.emptyMap());
            when(mockUserIdService.getOrCreateUserId()).thenReturn("user123");

            ArgumentCaptor<AmplitudeCallbacks> captor = ArgumentCaptor.forClass(AmplitudeCallbacks.class);

            sender.send(event);

            verify(mockAmplitude).logEvent(any(), captor.capture());
            AmplitudeCallbacks callbacks = captor.getValue();
            callbacks.onLogEventServerResponse(null, 200, "OK");
        }
    }

    @Test
    void testAmplitudeNotInitializedWithEmptyApiKey() {
        try (MockedStatic<Amplitude> amplitudeStatic = mockStatic(Amplitude.class)) {
            amplitudeStatic.when(Amplitude::getInstance).thenReturn(mockAmplitude);
            AmplitudeEventSender sender = new AmplitudeEventSender("", true);
            sender.setUserIdService(mockUserIdService);

            Event event = mock(Event.class);
            sender.send(event);

            verify(mockAmplitude, never()).logEvent(any(), any(AmplitudeCallbacks.class));
        }
    }

    @Test
    void testAmplitudeDisabledByConfig() {
        try (MockedStatic<Amplitude> amplitudeStatic = mockStatic(Amplitude.class)) {
            amplitudeStatic.when(Amplitude::getInstance).thenReturn(mockAmplitude);
            AmplitudeEventSender sender = new AmplitudeEventSender(VALID_API_KEY, false);
            sender.setUserIdService(mockUserIdService);

            Event event = mock(Event.class);
            sender.send(event);

            verify(mockAmplitude, never()).logEvent(any(), any(AmplitudeCallbacks.class));
        }
    }

    @Test
    void testShutdown() throws InterruptedException {
        try (MockedStatic<Amplitude> amplitudeStatic = mockStatic(Amplitude.class)) {
            amplitudeStatic.when(Amplitude::getInstance).thenReturn(mockAmplitude);
            AmplitudeEventSender sender = new AmplitudeEventSender(VALID_API_KEY, true);
            sender.setUserIdService(mockUserIdService);
            sender.shutdown();
            verify(mockAmplitude).flushEvents();
            verify(mockAmplitude).shutdown();
        }
    }
}