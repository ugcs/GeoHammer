package com.ugcs.geohammer.model.event;

import com.ugcs.geohammer.model.Task;

public class TaskCompletedEvent extends TaskEvent {

    public TaskCompletedEvent(Object source, Task<?> task) {
        super(source, task);
    }
}
