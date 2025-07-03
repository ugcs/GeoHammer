package com.ugcs.gprvisualizer.analytics;

public interface EventSender {
    void send(Event event);

    void shutdown();
}
