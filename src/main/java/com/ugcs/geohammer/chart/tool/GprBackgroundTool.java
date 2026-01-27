package com.ugcs.geohammer.chart.tool;

import com.ugcs.geohammer.ProfileView;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.event.FileOpenedEvent;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
import com.ugcs.geohammer.model.event.WhatChanged;
import com.ugcs.geohammer.model.undo.UndoModel;
import com.ugcs.geohammer.service.gpr.BackgroundNoiseRemover;
import com.ugcs.geohammer.service.gpr.CommandRegistry;
import com.ugcs.geohammer.service.gpr.SpreadCoordinates;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;

@Component
public class GprBackgroundTool extends FilterToolView {

    private final Model model;

    private final UndoModel undoModel;

    private final CommandRegistry commandRegistry;

    private final ProfileView profileView;

    private final Button spreadCoordinates;

    private final Button removeBackground;

    public GprBackgroundTool(
            Model model,
            UndoModel undoModel,
            CommandRegistry commandRegistry,
            ProfileView profileView,
            ExecutorService executor
    ) {
        super(executor);

        this.model = model;
        this.undoModel = undoModel;
        this.commandRegistry = commandRegistry;
        this.profileView = profileView;

        spreadCoordinates = new Button("Spread coordinates");
        spreadCoordinates.setOnAction(event -> spreadCoordinates());
        spreadCoordinates.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(spreadCoordinates, Priority.ALWAYS);

        removeBackground = new Button("Remove background");
        removeBackground.setOnAction(event -> removeBackground());
        removeBackground.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(removeBackground, Priority.ALWAYS);

        // disable base filter buttons
        buttonContainer.setVisible(false);
        buttonContainer.setManaged(false);
    }

    private void spreadCoordinates() {
        submitAction(() -> {
            commandRegistry.runForGprFiles(
                    model.getFileManager().getGprFiles(),
                    new SpreadCoordinates());
            Platform.runLater(this::updateView);
            return null;
        });
    }

    private void removeBackground() {
        submitAction(() -> {
            commandRegistry.runForGprFiles(
                    model.getFileManager().getGprFiles(),
                    new BackgroundNoiseRemover(undoModel));
            return null;
        });
    }

    @Override
    public boolean isVisibleFor(SgyFile file) {
        return file instanceof TraceFile;
    }

    @Override
    public void updateView() {
        spreadCoordinates.setDisable(!model.isSpreadCoordinatesNecessary());
        inputContainer.getChildren().clear();
        if (selectedFile instanceof TraceFile traceFile) {
            inputContainer.getChildren().addAll(profileView.getRight(traceFile));
            HBox buttons = new HBox(Tools.DEFAULT_SPACING,
                    removeBackground,
                    spreadCoordinates);
            inputContainer.getChildren().add(buttons);
        }
    }

    @EventListener
    private void onFileSelected(FileSelectedEvent event) {
        Platform.runLater(() -> selectFile(event.getFile()));
    }

    @EventListener
    private void onFileOpened(FileOpenedEvent event) {
        Platform.runLater(this::updateView);
    }

    @EventListener
    private void onSomethingChanged(WhatChanged changed) {
        if (changed.isUpdateButtons() || changed.isTraceCut()) {
            Platform.runLater(this::updateView);
        }
    }
}
