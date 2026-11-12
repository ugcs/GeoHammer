package com.ugcs.geohammer.service.demo;

import com.ugcs.geohammer.Loader;
import com.ugcs.geohammer.analytics.EventSender;
import com.ugcs.geohammer.analytics.EventsFactory;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Result;
import javafx.application.Platform;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

@Service
public class DemoService {

    private final DemoFiles demoFiles;

    private final Loader loader;

    private final ExecutorService executor;

    private final EventSender eventSender;

    private final EventsFactory eventsFactory;

    public DemoService(DemoFiles demoFiles, Loader loader, ExecutorService executor,
            EventSender eventSender, EventsFactory eventsFactory) {
        Check.notNull(demoFiles);
        Check.notNull(loader);
        Check.notNull(executor);
        Check.notNull(eventSender);
        Check.notNull(eventsFactory);

        this.demoFiles = demoFiles;
        this.loader = loader;
        this.executor = executor;
        this.eventSender = eventSender;
        this.eventsFactory = eventsFactory;
    }

    public CompletableFuture<Void> openDemo(DemoFile demoFile) {
        Check.notNull(demoFile);

        return CompletableFuture.runAsync(() -> {
            File copy = createCopy(demoFile);
            Future<Map<File, Result<File>>> loading = CompletableFuture
                    .supplyAsync(() -> loader.load(List.of(copy)), Platform::runLater)
                    .join();
            try {
                Result<File> result = loading.get().get(copy);
                if (result != null && result.isSuccess()) {
                    eventSender.send(eventsFactory.createDemoFileOpenedEvent(demoFile));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CancellationException("Demo loading interrupted");
            } catch (ExecutionException e) {
                throw new CompletionException(e.getCause());
            }
        }, executor);
    }

    private File createCopy(DemoFile demoFile) {
        try {
            return demoFiles.createCopy(demoFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
