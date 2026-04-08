package com.ugcs.geohammer.chart.tool.projection;

import com.github.thecoldwine.sigrun.common.TraceHeader;
import com.ugcs.geohammer.chart.tool.projection.model.Grid;
import com.ugcs.geohammer.chart.tool.projection.model.GridOptions;
import com.ugcs.geohammer.chart.tool.projection.model.ProjectionModel;
import com.ugcs.geohammer.chart.tool.projection.model.ProjectionOptions;
import com.ugcs.geohammer.chart.tool.projection.model.ProjectionResult;
import com.ugcs.geohammer.chart.tool.projection.model.RenderOptions;
import com.ugcs.geohammer.chart.tool.projection.model.TraceProfile;
import com.ugcs.geohammer.chart.tool.projection.model.TraceSelection;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.util.SinglePendingExecutor;
import com.ugcs.geohammer.view.Listeners;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

@Component
public class ProjectionController {

    private final ProjectionModel projectionModel;

    private final TraceProfileService traceProfileService;

    private final GridService gridService;

    private final SinglePendingExecutor profileUpdater;

    private final SinglePendingExecutor gridUpdater;

    public ProjectionController(
            ProjectionModel projectionModel,
            TraceProfileService traceProfileService,
            GridService gridService,
            ExecutorService executor) {
        this.projectionModel = projectionModel;
        this.traceProfileService = traceProfileService;
        this.gridService = gridService;

        profileUpdater = new SinglePendingExecutor(executor);
        gridUpdater = new SinglePendingExecutor(executor);

        initListeners();
    }

    private void initListeners() {
        TraceSelection selection = projectionModel.getSelection();
        Listeners.onChange(selection.fileProperty(), v -> {
            projectionModel.getResult().gridProperty().set(null);
            initFile();
        });
        Listeners.onChange(selection.lineProperty(), v -> {
            projectionModel.getResult().gridProperty().set(null);
            updateTraceProfile();
        });

        RenderOptions renderOptions = projectionModel.getRenderOptions();
        Listeners.onChange(renderOptions.removeBackgroundProperty(), v -> updateTraceProfile());

        ProjectionOptions projectionOptions = projectionModel.getProjectionOptions();
        Listeners.onChange(projectionOptions.sampleOffsetProperty(), v -> updateTraceProfile());
        Listeners.onChange(projectionOptions.centerFrequencyProperty(), v -> updateTraceProfile());
        Listeners.onChange(projectionOptions.relativePermittivityProperty(), v -> updateTraceProfile());
        Listeners.onChange(projectionOptions.antennaOffsetProperty(), v -> updateTraceProfile());
        Listeners.onChange(projectionOptions.terrainOffsetProperty(), v -> updateTraceProfile());
        Listeners.onChange(projectionOptions.antennaSmoothingRadiusProperty(), v -> updateTraceProfile());
        Listeners.onChange(projectionOptions.terrainSmoothingRadiusProperty(), v -> updateTraceProfile());
        Listeners.onChange(projectionOptions.normalWeightProperty(), v -> updateTraceProfile());

        GridOptions gridOptions = projectionModel.getGridOptions();
        Listeners.onChange(gridOptions.resolutionProperty(), v -> autoUpdateGrid());
        Listeners.onChange(gridOptions.migrationProperty(), v -> autoUpdateGrid());
        Listeners.onChange(gridOptions.refractionProperty(), v -> autoUpdateGrid());
        Listeners.onChange(gridOptions.fresnelApertureFactorProperty(), v -> autoUpdateGrid());
        Listeners.onChange(gridOptions.interpolateGridProperty(), v -> autoUpdateGrid());
        Listeners.onChange(gridOptions.cropAirProperty(), v -> autoUpdateGrid());

        ProjectionResult result = projectionModel.getResult();
        Listeners.onChange(result.profileProperty(), v -> autoUpdateGrid());
    }

    public void selectFile(TraceFile file) {
        TraceSelection selection = projectionModel.getSelection();
        if (Objects.equals(selection.getFile(), file)) {
            return;
        }

        selection.fileProperty().set(file);
    }

    private void initFile() {
        TraceSelection selection = projectionModel.getSelection();

        TraceFile file = selection.getFile();
        if (file == null) {
            // clear
            ProjectionResult result = projectionModel.getResult();
            result.gridProperty().set(null);
            result.profileProperty().set(null);
            return;
        }

        // init sample offset
        int sampleOffset = getPulseDelaySamples(file);
        projectionModel.getProjectionOptions().sampleOffsetProperty().set(sampleOffset);

        // init lines
        updateLines();

        // request zoom
        requestZoomToProfile();

        updateTraceProfile();
    }

    private int getPulseDelaySamples(TraceFile file) {
        List<Trace> traces = file.getTraces();
        if (traces.isEmpty()) {
            return 0;
        }
        int sampleInterval = file.getSampleInterval();
        if (sampleInterval == 0) {
            return 0;
        }
        Trace trace = traces.getFirst();
        TraceHeader header = trace != null ? trace.getHeader() : null;
        Short delay = header != null ? header.getDelayRecordingTime() : null;
        return delay != null
                ? Math.max(0, delay / sampleInterval)
                : 0;
    }

    public void updateLines() {
        TraceSelection selection = projectionModel.getSelection();
        TraceFile file = selection.getFile();
        if (file == null) {
            return;
        }

        // init lines
        Integer line = selection.getLine();
        NavigableMap<Integer, IndexRange> lineRanges = file.getLineRanges();
        if (line != null && !lineRanges.containsKey(line)) {
            line = null;
        }
        if (line == null && !lineRanges.isEmpty()) {
            line = lineRanges.firstKey();
        }
        ObservableList<Integer> fileLines = selection.getFileLines();
        fileLines.clear();
        fileLines.addAll(lineRanges.keySet());
        selection.lineProperty().set(line);
    }

    public void requestZoomToProfile() {
        projectionModel.getViewport().zoomToProfileProperty().set(true);
    }

    public void buildGrid() {
        updateGrid();
    }

    public void updateTraceProfile() {
        profileUpdater.submit(() -> {
            TraceProfile traceProfile = traceProfileService.buildTraceProfile();
            setTraceProfile(traceProfile);
        });
    }

    private void setTraceProfile(TraceProfile traceProfile) {
        Platform.runLater(() -> {
            projectionModel.getResult().profileProperty().setValue(traceProfile);
        });
    }

    public void autoUpdateGrid() {
        if (!projectionModel.getGridOptions().isAutoUpdate()) {
            return;
        }
        updateGrid();
    }

    public void updateGrid() {
        TraceProfile traceProfile = projectionModel.getResult().getProfile();
        gridUpdater.submit(() -> {
            Grid grid = null;
            if (traceProfile != null) {
                grid = gridService.buildGrid(traceProfile);
            }
            setGrid(grid);
        });
    }

    private void setGrid(Grid grid) {
        Platform.runLater(() -> {
            projectionModel.getResult().gridProperty().set(grid);
        });
    }
}
