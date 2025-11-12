package com.ugcs.geohammer.model;

import com.ugcs.geohammer.util.Check;

import java.util.concurrent.Future;

public class Task<V> {

    private final Future<V> future;

    private final String name;

    public Task(Future<V> future, String name) {
        Check.notNull(future);
        Check.notEmpty(name);

        this.future = future;
        this.name = name;
    }

    public Future<V> getFuture() {
        return future;
    }

    public String getName() {
        return name;
    }
}
