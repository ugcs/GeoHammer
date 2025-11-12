package com.ugcs.geohammer.analytics;

public interface EventSender {
    void send(Event event);

    void shutdown();
}
