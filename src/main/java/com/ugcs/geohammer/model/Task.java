package com.ugcs.geohammer.model;

import com.ugcs.geohammer.util.Check;

import java.util.concurrent.CompletableFuture;

public class Task<V> {

    private final CompletableFuture<V> future;

    private final String name;

    public Task(CompletableFuture<V> future, String name) {
        Check.notNull(future);
        Check.notEmpty(name);

        this.future = future;
        this.name = name;
    }

    public CompletableFuture<V> getFuture() {
        return future;
    }

    public String getName() {
        return name;
    }
}
