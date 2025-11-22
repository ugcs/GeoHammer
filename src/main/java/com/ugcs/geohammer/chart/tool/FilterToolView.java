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

import java.util.concurrent.ExecutorService;

public abstract class FilterToolView extends ToolView {

    private static final Logger log = LoggerFactory.getLogger(FilterToolView.class);

    protected final ExecutorService executor;

    // view

    protected final Button applyButton;

    protected final Button applyToAllButton;

    protected final ProgressIndicator progress;

    protected final VBox inputContainer;

    protected final HBox buttonContainer;

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
        applyToAllButton.setVisible(false); // disabled by default
        applyToAllButton.setDisable(true);

        // input
        inputContainer = new VBox(Tools.DEFAULT_SPACING);

        // buttons
        Region separator = new Region();
        HBox.setHgrow(separator, Priority.ALWAYS);
        buttonContainer = new HBox(Tools.DEFAULT_SPACING, applyButton, separator, applyToAllButton);

        VBox container = new VBox(Tools.DEFAULT_SPACING, inputContainer, buttonContainer);
        container.setPadding(Tools.DEFAULT_OPTIONS_INSETS);
        container.setVisible(false);
        container.setManaged(false);

        getChildren().addAll(container, progress);
    }

    public void showApply(boolean show) {
        applyButton.setVisible(show);
    }

    public void showApplyToAll(boolean show) {
        applyToAllButton.setVisible(show);
    }

    public void showProgress(boolean show) {
        progress.setVisible(show);
        progress.setManaged(show); // TODO ?
    }

    public void disableInput(boolean disable) {
        inputContainer.setDisable(disable);
    }

    public void disableActions(boolean disable) {
        applyButton.setDisable(disable);
        applyToAllButton.setDisable(disable);
    }

    protected void disableAndShowProgress() {
        showProgress(true);

        disableInput(true);
        disableActions(true);
    }

    protected void enableAndHideProgress() {
        disableInput(false);
        disableActions(false);

        showProgress(false);
    }

    private void onApply(ActionEvent event) {
        disableAndShowProgress();
        executor.submit(() -> {
            savePreferences();
            try {
                apply();
            } catch (Exception e) {
                log.error("Error", e);
            } finally {
                Platform.runLater(this::enableAndHideProgress);
            }
        });
    }

    public abstract void apply();

    private void onApplyToAll(ActionEvent event) {
        disableAndShowProgress();
        executor.submit(() -> {
            savePreferences();
            try {
                applyToAll();
            } catch (Exception e) {
                log.error("Error", e);
            } finally {
                Platform.runLater(this::enableAndHideProgress);
            }
        });
    }

    public abstract void applyToAll();
}
