package com.ugcs.gprvisualizer.app.service.task;

public class TaskCompletedEvent extends TaskEvent {

    public TaskCompletedEvent(Object source, Task<?> task) {
        super(source, task);
    }
}
