package com.ugcs.geohammer.model.event;

import com.ugcs.geohammer.model.Task;
import com.ugcs.geohammer.util.Check;

public abstract class TaskEvent extends BaseEvent {

    private final Task<?> task;

    public TaskEvent(Object source, Task<?> task) {
        super(source);

        Check.notNull(task);
        this.task = task;
    }

    public Task<?> getTask() {
        return task;
    }
}
