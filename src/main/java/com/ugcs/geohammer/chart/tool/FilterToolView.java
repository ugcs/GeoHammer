package com.ugcs.geohammer.chart.tool;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public abstract class FilterToolView extends ToolView {

    private static final Logger log = LoggerFactory.getLogger(FilterToolView.class);

    protected final ExecutorService executor;

    // view

    protected final ProgressIndicator progress;

    protected final VBox inputContainer;

    protected final HBox buttonContainer;

    protected final Button applyButton;

    protected final Button applyToAllButton;

    public FilterToolView(ExecutorService executor) {
        this.executor = executor;

        progress = new ProgressIndicator();
        progress.setVisible(false);
        progress.setManaged(false);

        applyButton = new Button("Apply");
        applyButton.setOnAction(this::onApply);
        applyButton.setVisible(true);
        applyButton.setDisable(true);

        applyToAllButton = new Button("Apply to all");
        applyToAllButton.setOnAction(this::onApplyToAll);
        applyToAllButton.setVisible(false); // hide by default
        applyToAllButton.setDisable(true);

        // input
        inputContainer = new VBox(Tools.DEFAULT_SPACING);

        // buttons
        Region separator = new Region();
        HBox.setHgrow(separator, Priority.ALWAYS);
        buttonContainer = new HBox(Tools.DEFAULT_SPACING, applyButton, separator, applyToAllButton);

        VBox container = Tools.createToolContainer(inputContainer, buttonContainer);
        getChildren().addAll(progress, container);
    }

    public void showApply(boolean show) {
        applyButton.setVisible(show);
    }

    public void showApplyToAll(boolean show) {
        applyToAllButton.setVisible(show);
    }

    public void showProgress(boolean show) {
        progress.setVisible(show);
        progress.setManaged(show);
    }

    public void disableInput(boolean disable) {
        inputContainer.setDisable(disable);
    }

    public void disableActions(boolean disable) {
        applyButton.setDisable(disable);
        applyToAllButton.setDisable(disable);
    }

    public void disableAndShowProgress() {
        showProgress(true);

        disableInput(true);
        disableActions(true);
    }

    public void enableAndHideProgress() {
        disableInput(false);
        disableActions(false);

        showProgress(false);
    }

    protected <T> Future<T> submitAction(Callable<T> action) {
        return submitAction(action, true);
    }

    protected <T> Future<T> submitAction(Callable<T> action, boolean showProgress) {
        if (showProgress) {
            disableAndShowProgress();
        }
        return executor.submit(() -> {
            savePreferences();
            try {
                return action.call();
            } catch (Exception e) {
                log.error("Error", e);
                throw e;
            } finally {
                if (showProgress) {
                    Platform.runLater(this::enableAndHideProgress);
                }
            }
        });
    }

    protected void onApply(ActionEvent event) {
    }

    protected void onApplyToAll(ActionEvent event) {
    }
}
