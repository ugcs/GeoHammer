package com.ugcs.geohammer.model.event;

import com.ugcs.geohammer.model.Task;

public class TaskRegisteredEvent extends TaskEvent {

    public TaskRegisteredEvent(Object source, Task<?> task) {
        super(source, task);
    }
}
