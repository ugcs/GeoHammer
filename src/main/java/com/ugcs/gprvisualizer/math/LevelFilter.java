package com.ugcs.gprvisualizer.math;

import java.io.File;
import java.util.List;
import java.util.Objects;

import com.github.thecoldwine.sigrun.common.ext.PositionFile;
import com.github.thecoldwine.sigrun.common.ext.TraceFile;

import com.ugcs.gprvisualizer.app.undo.FileSnapshot;
import com.ugcs.gprvisualizer.app.undo.UndoFrame;
import com.ugcs.gprvisualizer.app.undo.UndoModel;
import com.ugcs.gprvisualizer.event.FileOpenedEvent;
import com.ugcs.gprvisualizer.event.FileSelectedEvent;
import com.ugcs.gprvisualizer.event.WhatChanged;
import com.ugcs.gprvisualizer.gpr.Settings;
import com.ugcs.gprvisualizer.ui.BaseSlider;
import com.ugcs.gprvisualizer.utils.Strings;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
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

    private Label elevationSourceName;

    private ComboBox<String> traceColumnSelector;

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
                    updateView(selectedFile);
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
        // elevation source
        if (elevationSourceName == null) {
            elevationSourceName = new Label();
        }
        // trace semantic
        if (traceColumnSelector == null) {
            traceColumnSelector = new ComboBox<>();
            traceColumnSelector.setPrefWidth(200);
            traceColumnSelector.setOnAction(this::traceColumnSelected);
        }

        HBox traceColumn = new HBox();
        traceColumn.setAlignment(Pos.BASELINE_LEFT);
        traceColumn.setSpacing(8);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        traceColumn.getChildren().addAll(
                new Label("Trace column"),
                spacer,
                traceColumnSelector);

        VBox elevationSource = new VBox();
        elevationSource.setStyle("-fx-border-color: #cccccc;-fx-border-width: 1px;-fx-border-radius: 6px;");
        elevationSource.setSpacing(12);
        elevationSource.setPadding(new Insets(12));
        elevationSource.getChildren().addAll(
                elevationSourceName,
                traceColumn);

        if (buttonLevelGround == null) {
            buttonLevelGround = new Button("Flatten surface");
            buttonLevelGround.setMaxWidth(Double.MAX_VALUE);
            buttonLevelGround.setOnAction(this::flattenSurface);
        }

        if (slider == null) {
            OffsetSlider offsetSlider = new OffsetSlider();
            slider = offsetSlider.produce();
        }

        VBox vbox = new VBox();
        vbox.setSpacing(8);
        vbox.getChildren().addAll(List.of(
                elevationSource,
                slider,
                buttonLevelGround
        ));

        updateView(selectedFile);

        return List.of(vbox);
    }

    private void saveUndoSnapshot(TraceFile traceFile) {
        if (traceFile == null) {
            return;
        }

        FileSnapshot<TraceFile> snapshot = traceFile.createSnapshotWithTraces();
        undoModel.push(new UndoFrame(snapshot));
    }

    private void flattenSurface(ActionEvent event) {
        if (selectedFile instanceof TraceFile traceFile) {
            if (isGroundProfileExists(traceFile)) {
                saveUndoSnapshot(traceFile);
                commandRegistry.runForGprFiles(
                        List.of(traceFile),
                        new LevelGround());
                updateView(traceFile);
            }
        }
    }

    private void updateView(TraceFile traceFile) {
        if (elevationSourceName != null) {
            elevationSourceName.setText("Elevation source: " + getElevationSourceName(traceFile));
        }
        if (traceColumnSelector != null) {
            initTraceColumnSelector(traceFile);
        }
        if (buttonSpreadCoord != null) {
            buttonSpreadCoord.setDisable(!model.isSpreadCoordinatesNecessary());
        }
        if (buttonLevelGround != null) {
            buttonLevelGround.setDisable(!isGroundProfileExists(traceFile));
        }
        if (slider != null) {
            slider.setDisable(!isGroundProfileExists(traceFile));
        }
    }

    private String getElevationSourceName(TraceFile traceFile) {
        File file = null;
        if (traceFile != null) {
            PositionFile positionFile = traceFile.getGroundProfileSource();
            if (positionFile != null) {
                file = positionFile.getPositionFile();
            }
        }
        return file != null ? file.getName() : "not found";
    }

    private void initTraceColumnSelector(TraceFile traceFile) {
        traceColumnSelector.setDisable(!isGroundProfileExists(traceFile));
        traceColumnSelector.getItems().clear();

        PositionFile positionFile = traceFile != null
                ? traceFile.getGroundProfileSource()
                : null;
        if (positionFile != null) {
            traceColumnSelector.getItems().addAll(positionFile.getAvailableTraceSemantics());
            traceColumnSelector.setValue(traceFile.getGroundProfileTraceSemantic());
        } else {
            traceColumnSelector.setValue(null);
        }
    }

    private void traceColumnSelected(ActionEvent event) {
        TraceFile traceFile = selectedFile;
        if (traceFile == null) {
            return;
        }
        String traceSemantic = traceColumnSelector.getValue();
        if (Objects.equals(traceSemantic, traceFile.getGroundProfileTraceSemantic())) {
            return; // already selected
        }
        PositionFile positionFile = traceFile.getGroundProfileSource();
        if (positionFile != null) {
            positionFile.setGroundProfile(traceFile, traceSemantic);
            HorizontalProfile profile = traceFile.getGroundProfile();
            if (profile != null) {
                // apply offset
                int offset = levelSettings.levelPreviewShift.intValue();
                profile.setOffset(offset);
            }
            model.publishEvent(new WhatChanged(this, WhatChanged.Change.traceValues));
        }
    }

    protected boolean isGroundProfileExists(TraceFile traceFile) {
        return traceFile != null && traceFile.getGroundProfile() != null;
    }

    private void clearForNewFile(TraceFile traceFile) {
        updateView(traceFile);
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
