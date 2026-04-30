package com.ugcs.geohammer;

import com.ugcs.geohammer.model.Task;
import com.ugcs.geohammer.model.event.TaskCompletedEvent;
import com.ugcs.geohammer.model.event.TaskRegisteredEvent;
import com.ugcs.geohammer.service.TaskService;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Strings;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TaskStatusView extends HBox implements InitializingBean {

    private static final String DEFAULT_TITLE = "No tasks";

    private final TaskService taskService;

    private final Label title;

    private final Label cancel;

    private final ContextMenu taskMenu;
    
    public TaskStatusView(TaskService taskService) {
        this.taskService = taskService;

        this.title = new Label(DEFAULT_TITLE);
        this.cancel = new Label("Cancel");
        this.taskMenu = new ContextMenu();

        initView();
    }
    
    @Override
    public void afterPropertiesSet() {
        updateView(taskService.getTasks());
    }
    
    private void initView() {
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(6);

        title.setAlignment(Pos.BASELINE_RIGHT);
        title.setMinWidth(Region.USE_PREF_SIZE);
        title.setMaxWidth(Region.USE_PREF_SIZE);

        cancel.getStyleClass().addAll("status-action", "neutral");
        cancel.setOnMouseClicked(this::onCancelClick);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        getChildren().addAll(spacer, title, cancel);
        setVisible(true);
    }
    
    private void updateView(List<Task<?>> tasks) {
        Platform.runLater(() -> {
            int numTasks = Nulls.toEmpty(tasks).size();
            if (numTasks == 0) {
                title.setText(DEFAULT_TITLE);
            } else if (numTasks == 1) {
                String taskName = tasks.getFirst().getName();
                if (Strings.isNullOrEmpty(taskName)) {
                    title.setText("Task in progress");
                }
                title.setText(taskName);
            } else {
                title.setText(numTasks + " tasks in progress");
            }
            cancel.setDisable(numTasks == 0);
            setVisible(numTasks > 0);
        });
    }

    private void onCancelClick(MouseEvent e) {
        List<Task<?>> tasks = taskService.getTasks();
        if (tasks.isEmpty()) {
            return;
        }
        if (tasks.size() == 1) {
            taskService.cancelTask(tasks.getFirst());
        } else {
            if (taskMenu.isShowing()) {
                taskMenu.hide();
            } else {
                updateTaskMenu(tasks);
                taskMenu.show(cancel, Side.TOP, 0, 0);
            }
        }
    }

    private void updateTaskMenu(List<Task<?>> tasks) {
        taskMenu.getItems().clear();
        for (Task<?> task : tasks) {
            MenuItem selectTask = new MenuItem(task.getName());
            selectTask.setOnAction(event -> {
                taskService.cancelTask(task);
            });
            taskMenu.getItems().add(selectTask);
        }
    }

    @EventListener
    private void onTaskRegistered(TaskRegisteredEvent event) {
        updateView(taskService.getTasks());
    }

    @EventListener
    private void onTaskCompleted(TaskCompletedEvent event) {
        updateView(taskService.getTasks());
    }
}
