package com.ugcs.gprvisualizer.analytics.amplitude;

import com.amplitude.Amplitude;
import com.amplitude.AmplitudeCallbacks;
import com.github.thecoldwine.sigrun.common.ext.CsvFile;
import com.ugcs.gprvisualizer.analytics.Event;
import com.ugcs.gprvisualizer.analytics.EventType;
import com.ugcs.gprvisualizer.app.ext.FileManager;
import com.ugcs.gprvisualizer.app.parcers.csv.CsvParser;
import com.ugcs.gprvisualizer.app.service.UserIdService;
import com.ugcs.gprvisualizer.app.yaml.Template;
import com.ugcs.gprvisualizer.event.FileOpenErrorEvent;
import com.ugcs.gprvisualizer.event.FileOpenedEvent;
import com.ugcs.gprvisualizer.gpr.Model;
import com.ugcs.gprvisualizer.utils.FileTypeUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.File;
import java.util.Collections;
import java.util.List;

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
    void testListenFileOpenedEvent_withNullAmplitude() {
        AmplitudeEventSender sender = new AmplitudeEventSender("key", true);
        sender.setUserIdService(mockUserIdService);
        when(mockUserIdService.getOrCreateUserId()).thenReturn("test-user-id");
        AmplitudeEventSender spySender = spy(sender);
        FileOpenedEvent event = mock(FileOpenedEvent.class);
        spySender.listenFileOpenedEvent(event);
    }

    @Test
    void testListenFileOpenedEvent_withNonExistingFile() {
        try (MockedStatic<Amplitude> amplitudeStatic = mockStatic(Amplitude.class)) {
            amplitudeStatic.when(Amplitude::getInstance).thenReturn(mockAmplitude);
            AmplitudeEventSender sender = new AmplitudeEventSender(VALID_API_KEY, true);
            sender.setUserIdService(mockUserIdService);

            FileOpenedEvent event = mock(FileOpenedEvent.class);
            File file = mock(File.class);
            when(file.exists()).thenReturn(false);
            when(event.getFiles()).thenReturn(List.of(file));

            sender.listenFileOpenedEvent(event);
        }
    }

    @Test
    void testListenFileOpenedEvent_withCsvFileNotInModel() {
        try (MockedStatic<Amplitude> amplitudeStatic = mockStatic(Amplitude.class)) {
            amplitudeStatic.when(Amplitude::getInstance).thenReturn(mockAmplitude);
            AmplitudeEventSender sender = new AmplitudeEventSender(VALID_API_KEY, true);
            sender.setUserIdService(mockUserIdService);
            when(mockUserIdService.getOrCreateUserId()).thenReturn("test-user-id");

            FileOpenedEvent event = mock(FileOpenedEvent.class);
            File file = mock(File.class);
            when(file.exists()).thenReturn(true);
            when(event.getFiles()).thenReturn(List.of(file));

            try (MockedStatic<FileTypeUtils> fileTypeUtilsStatic = mockStatic(FileTypeUtils.class)) {
                fileTypeUtilsStatic.when(() -> FileTypeUtils.isCsvFile(file)).thenReturn(true);

                Model model = mock(Model.class);
                when(model.getFileManager()).thenReturn(mock(FileManager.class));
                when(model.getFileManager().getCsvFiles()).thenReturn(List.of());
                sender.setModel(model);

                sender.listenFileOpenedEvent(event);
            }
        }
    }

    @Test
    void testListenFileOpenedEvent_withCsvFileWithParser() {
        try (MockedStatic<Amplitude> amplitudeStatic = mockStatic(Amplitude.class);
             MockedStatic<FileTypeUtils> fileTypeUtilsStatic = mockStatic(FileTypeUtils.class)) {

            amplitudeStatic.when(Amplitude::getInstance).thenReturn(mockAmplitude);
            AmplitudeEventSender sender = new AmplitudeEventSender(VALID_API_KEY, true);
            sender.setUserIdService(mockUserIdService);

            FileOpenedEvent event = mock(FileOpenedEvent.class);
            File file = mock(File.class);
            when(file.exists()).thenReturn(true);
            when(event.getFiles()).thenReturn(List.of(file));
            when(mockUserIdService.getOrCreateUserId()).thenReturn("test-user-id");
            fileTypeUtilsStatic.when(() -> FileTypeUtils.isCsvFile(file)).thenReturn(true);

            CsvFile csvFile = mock(CsvFile.class);
            when(csvFile.getFile()).thenReturn(file);
            CsvParser parser = mock(CsvParser.class);
            when(csvFile.getParser()).thenReturn(parser);
            Template template = mock(Template.class);
            when(parser.getTemplate()).thenReturn(template);
            when(template.getName()).thenReturn("templateName");

            FileManager fileManager = mock(FileManager.class);
            when(fileManager.getCsvFiles()).thenReturn(List.of(csvFile));
            Model model = mock(Model.class);
            when(model.getFileManager()).thenReturn(fileManager);
            sender.setModel(model);

            sender.listenFileOpenedEvent(event);
            verify(mockAmplitude).logEvent(any(), any(AmplitudeCallbacks.class));
        }
    }

    @Test
    void testListenFileOpenedErrorEvent_withNullAmplitude() {
        AmplitudeEventSender sender = new AmplitudeEventSender("key", true);
        sender.setUserIdService(mockUserIdService);
        when(mockUserIdService.getOrCreateUserId()).thenReturn("test-user-id");
        AmplitudeEventSender spySender = spy(sender);
        FileOpenErrorEvent event = mock(FileOpenErrorEvent.class);
        spySender.listenFileOpenedErrorEvent(event);
        // Should log a warning, nothing else
    }

    @Test
    void testListenFileOpenedErrorEvent_withNonExistingFile() {
        try (MockedStatic<Amplitude> amplitudeStatic = mockStatic(Amplitude.class)) {
            amplitudeStatic.when(Amplitude::getInstance).thenReturn(mockAmplitude);
            AmplitudeEventSender sender = new AmplitudeEventSender(VALID_API_KEY, true);
            sender.setUserIdService(mockUserIdService);

            FileOpenErrorEvent event = mock(FileOpenErrorEvent.class);
            File file = mock(File.class);
            when(file.exists()).thenReturn(false);
            when(event.getFile()).thenReturn(file);

            sender.listenFileOpenedErrorEvent(event);
            // Should log a warning, nothing else
        }
    }

    @Test
    void testListenFileOpenedErrorEvent_withCsvFileNotInModel() {
        try (MockedStatic<Amplitude> amplitudeStatic = mockStatic(Amplitude.class)) {
            amplitudeStatic.when(Amplitude::getInstance).thenReturn(mockAmplitude);
            AmplitudeEventSender sender = new AmplitudeEventSender(VALID_API_KEY, true);
            sender.setUserIdService(mockUserIdService);
            when(mockUserIdService.getOrCreateUserId()).thenReturn("test-user-id");

            FileOpenErrorEvent event = mock(FileOpenErrorEvent.class);
            File file = mock(File.class);
            when(file.exists()).thenReturn(true);
            when(event.getFile()).thenReturn(file);
            when(event.getException()).thenReturn(new Exception("error"));
            mockStatic(FileTypeUtils.class).when(() -> FileTypeUtils.isCsvFile(file)).thenReturn(true);

            Model model = mock(Model.class);
            when(model.getFileManager()).thenReturn(mock(FileManager.class));
            when(model.getFileManager().getCsvFiles()).thenReturn(List.of());
            sender.setModel(model);


            sender.listenFileOpenedErrorEvent(event);
            verify(mockAmplitude).logEvent(any(), any(AmplitudeCallbacks.class));
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