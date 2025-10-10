package com.ugcs.gprvisualizer.app.service.task;

import com.ugcs.gprvisualizer.event.BaseEvent;
import com.ugcs.gprvisualizer.utils.Check;

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
