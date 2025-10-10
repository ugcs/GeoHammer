package com.ugcs.gprvisualizer.app.service.task;

public class TaskRegisteredEvent extends TaskEvent {

    public TaskRegisteredEvent(Object source, Task<?> task) {
        super(source, task);
    }
}
