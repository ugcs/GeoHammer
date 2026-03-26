package com.ugcs.geohammer.chart.tool.projection;

import com.github.thecoldwine.sigrun.common.TraceHeader;
import com.ugcs.geohammer.chart.tool.projection.math.Normal;
import com.ugcs.geohammer.chart.tool.projection.model.Grid;
import com.ugcs.geohammer.chart.tool.projection.model.GridOptions;
import com.ugcs.geohammer.chart.tool.projection.model.ProjectionResult;
import com.ugcs.geohammer.chart.tool.projection.model.RenderOptions;
import com.ugcs.geohammer.chart.tool.projection.model.TraceProfile;
import com.ugcs.geohammer.chart.tool.projection.model.ProjectionModel;
import com.ugcs.geohammer.chart.tool.projection.model.ProjectionOptions;
import com.ugcs.geohammer.chart.tool.projection.model.TraceSelection;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.SinglePendingExecutor;
import com.ugcs.geohammer.view.Listeners;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
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
        Listeners.onChange(projectionOptions.relativePermittivityProperty(), v -> updateTraceProfile());
        Listeners.onChange(projectionOptions.sampleOffsetProperty(), v -> updateTraceProfile());
        Listeners.onChange(projectionOptions.antennaOffsetProperty(), v -> updateTraceProfile());
        Listeners.onChange(projectionOptions.terrainOffsetProperty(), v -> updateTraceProfile());
        Listeners.onChange(projectionOptions.antennaSmoothingRadiusProperty(), v -> updateTraceProfile());
        Listeners.onChange(projectionOptions.terrainSmoothingRadiusProperty(), v -> updateTraceProfile());
        Listeners.onChange(projectionOptions.diffuseNormalsProperty(), v -> updateTraceProfile());

        GridOptions gridOptions = projectionModel.getGridOptions();
        Listeners.onChange(gridOptions.cellWidthProperty(), v -> updateGrid());
        Listeners.onChange(gridOptions.cellHeightProperty(), v -> updateGrid());
        Listeners.onChange(gridOptions.samplingMethodProperty(), v -> updateGrid());
        Listeners.onChange(gridOptions.interpolateGridProperty(), v -> updateGrid());
        Listeners.onChange(gridOptions.cropAirProperty(), v -> updateGrid());

        ProjectionResult result = projectionModel.getResult();
        Listeners.onChange(result.profileProperty(), v -> updateGrid());
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

    public void autoSizeGridCell() {
        TraceProfile traceProfile = projectionModel.getResult().getProfile();
        if (traceProfile == null) {
            return;
        }
        Point2D gridUnit = estimateGridUnit(traceProfile);
        projectionModel.getGridOptions().cellWidthProperty().set(gridUnit.getX());
        projectionModel.getGridOptions().cellHeightProperty().set(gridUnit.getY());
    }

    private Point2D estimateGridUnit(TraceProfile traceProfile) {
        Check.notNull(traceProfile);

        int numTraces = traceProfile.numTraces();
        int numSamples = traceProfile.numSamples();

        if (numTraces < 2) {
            return new Point2D(
                    GridOptions.DEFAULT_CELL_WIDTH,
                    GridOptions.DEFAULT_CELL_HEIGHT);
        }

        // w
        Point2D p0 = traceProfile.getOrigin(0);
        Point2D pn = traceProfile.getOrigin(numTraces - 1);
        double w = (pn.getX() - p0.getX()) / numTraces;

        // h
        int i = numTraces / 2;
        Normal normal = traceProfile.getNormal(i);
        double l0 = traceProfile.getSampleDepth(0, normal.length());
        double ln = traceProfile.getSampleDepth(numSamples - 1, normal.length());
        double h = (ln - l0) / numSamples;

        return new Point2D(w, h);
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
