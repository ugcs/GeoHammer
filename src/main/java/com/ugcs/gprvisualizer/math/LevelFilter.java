package com.ugcs.gprvisualizer.math;

import java.util.ArrayList;
import java.util.List;

import com.github.thecoldwine.sigrun.common.ext.TraceFile;

import com.ugcs.gprvisualizer.app.undo.UndoFrame;
import com.ugcs.gprvisualizer.app.undo.UndoModel;
import com.ugcs.gprvisualizer.app.undo.UndoSnapshot;
import com.ugcs.gprvisualizer.event.FileOpenedEvent;
import com.ugcs.gprvisualizer.event.FileSelectedEvent;
import com.ugcs.gprvisualizer.event.WhatChanged;
import com.ugcs.gprvisualizer.gpr.Settings;
import com.ugcs.gprvisualizer.ui.BaseSlider;
import com.ugcs.gprvisualizer.utils.Nulls;
import com.ugcs.gprvisualizer.utils.Strings;
import javafx.event.ActionEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ugcs.gprvisualizer.app.commands.BackgroundNoiseRemover;
import com.ugcs.gprvisualizer.app.commands.CommandRegistry;
import com.ugcs.gprvisualizer.app.commands.LevelGround;
import com.ugcs.gprvisualizer.app.commands.SpreadCoordinates;
import com.ugcs.gprvisualizer.draw.ToolProducer;
import com.ugcs.gprvisualizer.gpr.Model;

import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.control.Button;

@Component
public class LevelFilter implements ToolProducer {

    Settings levelSettings = new Settings();

    @Autowired
    private Model model;

    @Autowired
    private UndoModel undoModel;

    @Autowired
    private CommandRegistry commandRegistry;

    private Button buttonLevelGround;

    private Node slider;

    private Button buttonSpreadCoord;

    private TraceFile selectedFile;

    @EventListener
    private void selectFile(FileSelectedEvent event) {
        if (event.getFile() instanceof TraceFile traceFile) {
            selectedFile = traceFile;
        } else {
            selectedFile = null;
        }
        clearForNewFile(selectedFile);
    }

    @Override
    public List<Node> getToolNodes() {
        buttonSpreadCoord = commandRegistry.createButton(new SpreadCoordinates(),
                e -> {
                    updateButtons(selectedFile);
                });

        var buttons = List.of(commandRegistry.createButton(new BackgroundNoiseRemover()), buttonSpreadCoord);

        HBox hbox = new HBox();
        hbox.setSpacing(8);

        buttons.forEach(b -> {
            b.setMaxWidth(Double.MAX_VALUE);
        });

        HBox.setHgrow(buttons.get(0), Priority.ALWAYS);
        HBox.setHgrow(buttons.get(1), Priority.ALWAYS);

        hbox.getChildren().addAll(buttons);

        return List.of(hbox);
    }

    public List<Node> getToolNodes2() {
        if (buttonLevelGround == null) {
            buttonLevelGround = new Button("Flatten surface");
            buttonLevelGround.setOnAction(this::flattenSurface);
        }

        if (slider == null) {
            OffsetSlider offsetSlider = new OffsetSlider();
            slider = offsetSlider.produce();
        }

        List<Node> result = new ArrayList<>();

        HBox hbox = new HBox();
        hbox.setSpacing(8);

        buttonLevelGround.setMaxWidth(Double.MAX_VALUE);

        HBox.setHgrow(buttonLevelGround, Priority.ALWAYS);

        hbox.getChildren().addAll(buttonLevelGround);
        result.addAll(List.of(
                slider,
                buttonLevelGround
        ));

        VBox vbox = new VBox();
        vbox.setSpacing(5);

        updateButtons(selectedFile);

        vbox.getChildren().addAll(result);

        return List.of(vbox);
    }

    private void saveUndoSnapshots(List<TraceFile> files) {
        files = Nulls.toEmpty(files);
        ArrayList<UndoSnapshot> snapshots = new ArrayList<>(files.size());
        for (TraceFile file : files) {
            if (file.getGroundProfile() == null) {
                continue;
            }
            snapshots.add(file.createSnapshotWithTraces());
        }
        if (!snapshots.isEmpty()) {
            undoModel.push(new UndoFrame(snapshots));
        }
    }

    private void flattenSurface(ActionEvent event) {
        saveUndoSnapshots(model.getFileManager().getGprFiles());
        commandRegistry.runForGprFiles(new LevelGround());
        updateButtons(selectedFile);
    }

    private void updateButtons(TraceFile file) {
        if (buttonSpreadCoord != null) {
            buttonSpreadCoord.setDisable(!model.isSpreadCoordinatesNecessary());
        }
        if (buttonLevelGround != null) {
            buttonLevelGround.setDisable(!isGroundProfileExists(file));
        }
        if (slider != null) {
            slider.setDisable(!isGroundProfileExists(file));
        }
    }

    protected boolean isGroundProfileExists(TraceFile file) {
        return file != null && file.getGroundProfile() != null;
    }

    private void clearForNewFile(TraceFile file) {
        updateButtons(file);
    }

    @EventListener
    private void somethingChanged(WhatChanged changed) {
        if (changed.isUpdateButtons() || changed.isTraceCut()) {
            clearForNewFile(selectedFile);

            if (buttonSpreadCoord != null) {
                buttonSpreadCoord.setDisable(!model.isSpreadCoordinatesNecessary());
            }
        }
    }

    @EventListener
    private void fileOpened(FileOpenedEvent event) {
        if (buttonSpreadCoord != null) {
            buttonSpreadCoord.setDisable(!model.isSpreadCoordinatesNecessary());
        }
    }

    public void sliderChanged(ObservableValue<? extends Number> observable,
                        Number oldValue, Number newValue) {
        if (selectedFile != null) {
            int value = levelSettings.levelPreviewShift.intValue();
            HorizontalProfile profile = selectedFile.getGroundProfile();
            if (profile != null) {
                profile.setOffset(value);
                model.publishEvent(new WhatChanged(this, WhatChanged.Change.traceValues));
            }
        }
    }

    class OffsetSlider extends BaseSlider {

        private static final int LENGTH = 100;
        // when slider gets to an edge in a given factor
        // of its length it would shift its range toward the edge
        private static final float EXPAND_THRESHOLD = 0.2f;

        public OffsetSlider() {
            super(levelSettings, LevelFilter.this::sliderChanged);
            name = "Elevation lag,\ntraces";
            units = Strings.empty();
            tickUnits = 5;
        }

        @Override
        public int updateModel() {
            int value = (int)slider.getValue();
            levelSettings.levelPreviewShift.setValue(value);
            return value;
        }

        @Override
        public void updateUI() {
            int value = levelSettings.levelPreviewShift.intValue();
            int halfLength = LENGTH / 2;
            slider.setMin(value - halfLength);
            slider.setMax(value + halfLength);
            slider.setValue(value);

            slider.setOnMouseReleased(event -> {
                // expand slider range when value is close to edges
                double width = slider.getMax() - slider.getMin();
                double position = slider.getValue() - slider.getMin();
                double margin = EXPAND_THRESHOLD * width;
                int offset = 0;
                if (position < margin) {
                    offset = (int)-margin;
                }
                if (position > width - margin) {
                    offset = (int)margin;
                }
                if (offset != 0) {
                    slider.setMin((int)slider.getMin() + offset);
                    slider.setMax((int)slider.getMax() + offset);
                }
            });
        }
    }
}
