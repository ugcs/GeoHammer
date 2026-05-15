package com.ugcs.geohammer.util;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public final class Tasks {

    private Tasks() {
    }

    public static <T> CompletableFuture<T> submitCompletable(ExecutorService executor, Callable<T> task) {
        Check.notNull(executor);
        Check.notNull(task);

        CompletableFuture<T> completable = new CompletableFuture<>();
        Future<T> future = executor.submit(() -> {
            try {
                T value = task.call();
                completable.complete(value);
                return value;
            } catch (Throwable t) {
                completable.completeExceptionally(t);
                throw t;
            }
        });

        // propagate cancellation back to the worker
        completable.whenComplete((value, t) -> {
            if (completable.isCancelled()) {
                future.cancel(true);
            }
        });

        return completable;
    }
}
