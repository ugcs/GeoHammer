package com.ugcs.gprvisualizer.app.service.task;

import com.ugcs.gprvisualizer.utils.Check;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;

@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final ApplicationEventPublisher eventPublisher;

    private final List<Task<?>> tasks = new CopyOnWriteArrayList<>();

    public TaskService(ApplicationEventPublisher eventPublisher) {
        Check.notNull(eventPublisher);

        this.eventPublisher = eventPublisher;
    }

    public <V> CompletableFuture<V> registerTask(Future<V> future, String taskName) {
        return registerTask(new Task<>(future, taskName));
    }

    public <V> CompletableFuture<V> registerTask(Task<V> task) {
        Check.notNull(task);

        tasks.add(task);
        eventPublisher.publishEvent(new TaskRegisteredEvent(this, task));

        log.debug("Task registered: {}", task.getName());

        CompletableFuture<V> completableFuture;
        if (task.getFuture() instanceof CompletableFuture) {
            completableFuture = (CompletableFuture<V>)task.getFuture();
        } else {
            completableFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return task.getFuture().get();
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        // cleanup on completion
        completableFuture.whenComplete((result, exception) -> {
            if (exception != null) {
                log.warn("Task failed: {}", task.getName(), exception);
            } else {
                log.debug("Task completed: {}", task.getName());
            }

            tasks.remove(task);
            eventPublisher.publishEvent(new TaskCompletedEvent(this, task));
        });

        return completableFuture;
    }

    public <V> void cancelTask(Task<V> task) {
        Check.notNull(task);
        task.getFuture().cancel(true);
    }

    public List<Task<?>> getTasks() {
        return new ArrayList<>(tasks);
    }
}
