package com.ugcs.geohammer.chart.tool;

import com.ugcs.geohammer.chart.Chart;
import com.ugcs.geohammer.format.HorizontalProfile;
import com.ugcs.geohammer.format.PositionFile;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.Range;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
import com.ugcs.geohammer.model.event.WhatChanged;
import com.ugcs.geohammer.model.undo.FileSnapshot;
import com.ugcs.geohammer.model.undo.UndoFrame;
import com.ugcs.geohammer.model.undo.UndoModel;
import com.ugcs.geohammer.util.SinglePendingExecutor;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.view.ExpandableSlider;
import com.ugcs.geohammer.view.Views;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Component
public class GprElevationTool extends FilterToolView {

    private final Model model;

    private final UndoModel undoModel;

    private final SinglePendingExecutor pendingExecutor;

    private final Label elevationSourceName;

    private final ComboBox<String> traceHeaderSelector;

    private final ComboBox<String> altitudeHeaderSelector;

    private final ComboBox<String> ellipsoidalHeightHeaderSelector;

    private final Button flattenSurface;

    private final ExpandableSlider traceOffsetSlider;

    private final ExpandableSlider sampleOffsetSlider;

    private final ExpandableSlider peakWindowSlider;

    private final ExpandableSlider surfaceFilterWindowSlider;

    private final CheckBox detectPeaks;

    private final CheckBox removeAirGap;

    public GprElevationTool(Model model, UndoModel undoModel, ExecutorService executor) {
        super(executor);

        this.model = model;
        this.undoModel = undoModel;
        this.pendingExecutor = new SinglePendingExecutor(executor);

        // position file name
        elevationSourceName = new Label();

        // trace header
        traceHeaderSelector = new ComboBox<>();
        traceHeaderSelector.setPrefWidth(200);
        traceHeaderSelector.setOnAction(event -> updateProfile());

        HBox traceHeader = new HBox(Tools.DEFAULT_SPACING,
                new Label("Trace index"),
                Views.createSpacer(),
                traceHeaderSelector);
        traceHeader.setAlignment(Pos.BASELINE_LEFT);

        // altitude header
        altitudeHeaderSelector = new ComboBox<>();
        altitudeHeaderSelector.setPrefWidth(200);
        altitudeHeaderSelector.setOnAction(event -> updateProfile());

        HBox altitudeHeader = new HBox(Tools.DEFAULT_SPACING,
                new Label("Altitude AGL"),
                Views.createSpacer(),
                altitudeHeaderSelector);
        altitudeHeader.setAlignment(Pos.BASELINE_LEFT);

        // ellipsoidal height header
        ellipsoidalHeightHeaderSelector = new ComboBox<>();
        ellipsoidalHeightHeaderSelector.setPrefWidth(200);
        //ellipsoidalHeightHeaderSelector.setOnAction(event -> updateProfile());

        HBox ellipsoidalHeightHeader = new HBox(Tools.DEFAULT_SPACING,
                new Label("Ellipsoidal height"),
                Views.createSpacer(),
                ellipsoidalHeightHeaderSelector);
        altitudeHeader.setAlignment(Pos.BASELINE_LEFT);

        VBox elevationSource = new VBox(8,
                elevationSourceName,
                traceHeader,
                altitudeHeader,
                ellipsoidalHeightHeader);
        elevationSource.setStyle("-fx-border-color: #cccccc;-fx-border-width: 1px;-fx-border-radius: 6px;");
        elevationSource.setPadding(new Insets(8));

        traceOffsetSlider = new ExpandableSlider(
                "Trace lag,\ntraces", 0, 200) {
            @Override
            public void onValue(int value) {
                updateProfile();
            }
        };

        sampleOffsetSlider = new ExpandableSlider(
                "Sample offset,\nsamples", 0, 200) {
            @Override
            public void onValue(int value) {
                updateProfile();
            }
        };

        detectPeaks = new CheckBox("Detect peaks");
        detectPeaks.selectedProperty().addListener((observable, oldValue, newValue) -> {
            updateProfile();
        });

        Range peakWindowRange = new Range(0, 100);
        peakWindowSlider = new ExpandableSlider(
                "Window,\nsamples", 8, 30, peakWindowRange) {
            @Override
            public void onValue(int value) {
                updateProfile();
            }
        };

        Range surfaceFilterWindowRange = new Range(0, 1000);
        surfaceFilterWindowSlider = new ExpandableSlider(
                "Filter window,\ntraces", 25, 100, surfaceFilterWindowRange) {
            @Override
            public void onValue(int value) {
                updateProfile();
            }
        };

        VBox surfaceOptions = new VBox(Tools.DEFAULT_SPACING,
                detectPeaks,
                peakWindowSlider,
                surfaceFilterWindowSlider);
        surfaceOptions.setStyle("-fx-border-color: #cccccc;-fx-border-width: 1px;-fx-border-radius: 6px;");
        surfaceOptions.setPadding(new Insets(8));

        removeAirGap = new CheckBox("Remove air gap");
        removeAirGap.setSelected(true);

        // flatten surface button
        flattenSurface = new Button("Flatten surface");
        flattenSurface.setPrefWidth(150);
        flattenSurface.setMaxWidth(150);
        flattenSurface.setOnAction(event -> flattenSurface());

        HBox flattenSurfaceContainer = new HBox(Tools.DEFAULT_SPACING,
                removeAirGap,
                Views.createSpacer(),
                flattenSurface);
        flattenSurfaceContainer.setAlignment(Pos.BASELINE_LEFT);
        inputContainer.getChildren().setAll(
                elevationSource,
                traceOffsetSlider,
                sampleOffsetSlider,
                surfaceOptions,
                flattenSurfaceContainer);

        // disable base filter buttons
        buttonContainer.setVisible(false);
        buttonContainer.setManaged(false);
    }

    @Override
    public boolean isVisibleFor(SgyFile file) {
        return file instanceof TraceFile;
    }

    @Override
    public void updateView() {
        TraceFile traceFile = null;
        if (selectedFile instanceof TraceFile) {
            traceFile = (TraceFile)selectedFile;
        }

        String positionFileName = getPositionFileName(traceFile);
        elevationSourceName.setText(!Strings.isNullOrEmpty(positionFileName)
                ? "Elevation source: " + positionFileName
                : "No position file");

        initTraceHeaderSelector(traceFile);
        initAltitudeHeaderSelector(traceFile);
        initEllipsoidalHeightColumnSelector(traceFile);

        HorizontalProfile profile = getGroundProfile(traceFile);
        if (profile != null) {
            traceOffsetSlider.reset(profile.getTraceOffset());
            sampleOffsetSlider.reset(profile.getSampleOffset());
            detectPeaks.setSelected(profile.isDetectPeaks());
            peakWindowSlider.reset(profile.getPeakWindow());
            surfaceFilterWindowSlider.reset(profile.getSurfaceFilterWindow());
        }
        disableInput(profile == null);
    }

    private HorizontalProfile getGroundProfile(TraceFile traceFile) {
        if (traceFile == null) {
            return null;
        }
        return traceFile.getGroundProfile();
    }

    private String getPositionFileName(TraceFile traceFile) {
        if (traceFile != null) {
            PositionFile positionFile = traceFile.getPositionFile();
            if (positionFile != null) {
                File file = positionFile.getFile();
                if (file != null) {
                    return file.getName();
                }
            }
        }
        return null;
    }

    private void initTraceHeaderSelector(TraceFile traceFile) {
        traceHeaderSelector.setOnAction(null); // clear change listener

        PositionFile positionFile = traceFile != null
                ? traceFile.getPositionFile()
                : null;

        if (positionFile != null) {
            traceHeaderSelector.getItems().setAll(positionFile.getAvailableTraceHeaders());
            traceHeaderSelector.setValue(positionFile.getTraceHeader());
        } else {
            traceHeaderSelector.getItems().clear();
            traceHeaderSelector.setValue(null);
        }

        // restore change listener
        traceHeaderSelector.setOnAction(event -> updateProfile());
    }

    private void initAltitudeHeaderSelector(TraceFile traceFile) {
        altitudeHeaderSelector.setOnAction(null); // clear change listener

        PositionFile positionFile = traceFile != null
                ? traceFile.getPositionFile()
                : null;

        if (positionFile != null) {
            altitudeHeaderSelector.getItems().setAll(positionFile.getAvailableAltitudeHeaders());
            altitudeHeaderSelector.setValue(positionFile.getAltitudeHeader());
        } else {
            altitudeHeaderSelector.getItems().clear();
            altitudeHeaderSelector.setValue(null);
        }

        // restore change listener
        altitudeHeaderSelector.setOnAction(event -> updateProfile());
    }

    private void initEllipsoidalHeightColumnSelector(TraceFile traceFile) {
        PositionFile positionFile = traceFile != null
                ? traceFile.getPositionFile()
                : null;

        if (positionFile != null) {
            ellipsoidalHeightHeaderSelector.getItems().setAll(positionFile.getAvailableEllipsoidalHeightHeaders());
            ellipsoidalHeightHeaderSelector.setValue(positionFile.getEllipsoidalHeightHeader());
        } else {
            ellipsoidalHeightHeaderSelector.getItems().clear();
            ellipsoidalHeightHeaderSelector.setValue(null);
        }
    }

    private void saveUndoSnapshot(TraceFile traceFile) {
        if (traceFile == null) {
            return;
        }

        FileSnapshot<TraceFile> snapshot = traceFile.createSnapshotWithTraces();
        undoModel.push(new UndoFrame(snapshot));
    }

    private void flattenSurface() {
        if (selectedFile instanceof TraceFile traceFile) {
            HorizontalProfile groundProfile = getGroundProfile(traceFile);
            if (groundProfile != null) {
                submitAction(() -> {
                    saveUndoSnapshot(traceFile);
                    groundProfile.flattenSurface(traceFile, removeAirGap.isSelected());
                    // reload chart
                    Chart chart = model.getChart(traceFile);
                    if (chart != null) {
                        Platform.runLater(chart::reload);
                    }
                    Platform.runLater(this::updateView);
                    model.publishEvent(new WhatChanged(this, WhatChanged.Change.traceValues));
                    return null;
                });
            }
        }
    }

    private void updateProfile() {
        if (!(selectedFile instanceof TraceFile traceFile)) {
            return;
        }
        HorizontalProfile profile = traceFile.getGroundProfile();
        if (profile == null) {
            return;
        }
        PositionFile positionFile = traceFile.getPositionFile();
        if (positionFile == null) {
            return;
        }

        CompletableFuture<Void> future = pendingExecutor.submit(() -> {
            String traceHeader = traceHeaderSelector.getValue();
            String altitudeHeader = altitudeHeaderSelector.getValue();
            String ellipsoidalHeightHeader = ellipsoidalHeightHeaderSelector.getValue();

            if (!Objects.equals(traceHeader, positionFile.getTraceHeader())
                    || !Objects.equals(altitudeHeader, positionFile.getAltitudeHeader())) {
                positionFile.setTraceHeader(traceHeader);
                positionFile.setAltitudeHeader(altitudeHeader);
                profile.setAltitudes(positionFile.traceValues(
                        traceFile, positionFile.getAltitudeHeader()));
            }
            if (!Objects.equals(traceHeader, positionFile.getTraceHeader())
                    || !Objects.equals(ellipsoidalHeightHeader, positionFile.getEllipsoidalHeightHeader())) {
                positionFile.setTraceHeader(traceHeader);
                positionFile.setEllipsoidalHeightHeader(ellipsoidalHeightHeader);
                profile.setEllipsoidalHeights(positionFile.traceValues(
                        traceFile, positionFile.getEllipsoidalHeightHeader()));
            }

            profile.setTraceOffset(traceOffsetSlider.getValue());
            profile.setSampleOffset(sampleOffsetSlider.getValue());
            profile.setDetectPeaks(detectPeaks.isSelected());
            profile.setPeakWindow(Math.max(0, peakWindowSlider.getValue()));
            profile.setSurfaceFilterWindow(Math.max(0, surfaceFilterWindowSlider.getValue()));
            profile.buildSurface(traceFile);
            return null;
        });
        future.whenComplete((result, error) -> {
            if (error == null) {
                model.publishEvent(new WhatChanged(this, WhatChanged.Change.traceValues));
            }
        });
    }


    @EventListener
    private void onFileSelected(FileSelectedEvent event) {
        Platform.runLater(() -> selectFile(event.getFile()));
    }

    @EventListener
    private void onSomethingChanged(WhatChanged changed) {
        if (changed.isUpdateButtons() || changed.isTraceCut()) {
            Platform.runLater(this::updateView);
        }
    }
}
