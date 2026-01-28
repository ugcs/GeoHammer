package com.ugcs.geohammer.util;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class SinglePendingExecutor {

    private final Executor executor;

    // only last submitted kept in a queue
    private Runnable next;

    private boolean running;

    public SinglePendingExecutor(Executor executor) {
        this.executor = executor;
    }

    private Runnable wrap(Runnable task) {
        return () -> {
            try {
                task.run();
            } finally {
                scheduleNext();
            }
        };
    }

    private void scheduleNext() {
        Runnable task;
        synchronized (this) {
            task = next;
            next = null;
            if (task == null) {
                running = false;
                return;
            }
        }
        executor.execute(wrap(task));
    }

    public void submit(Runnable task) {
        boolean shouldExecute;
        synchronized (this) {
            shouldExecute = !running;
            if (shouldExecute) {
                running = true;
            } else {
                next = task; // replace waiting task
            }
        }
        if (shouldExecute) {
            executor.execute(wrap(task));
        }
    }

    public <T> CompletableFuture<T> submit(Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        submit(() -> {
            try {
                future.complete(task.call());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }
}
