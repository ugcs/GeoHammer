package com.ugcs.geohammer.map.layer;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import com.ugcs.geohammer.map.ThrQueue;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.model.MapField;
import com.ugcs.geohammer.map.MapView;
import com.ugcs.geohammer.model.event.FileRenameEvent;
import com.ugcs.geohammer.chart.OptionPane;
import com.ugcs.geohammer.chart.csv.SensorLineChart;
import com.ugcs.geohammer.model.event.FileClosedEvent;
import com.ugcs.geohammer.service.gridding.GriddingResult;
import com.ugcs.geohammer.service.gridding.GriddingService;
import com.ugcs.geohammer.service.TaskService;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
import com.ugcs.geohammer.model.event.GriddingParamsSetted;
import com.ugcs.geohammer.model.event.WhatChanged;
import com.ugcs.geohammer.math.AnalyticSignal;
import com.ugcs.geohammer.math.AnalyticSignalFilter;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.model.Range;
import com.ugcs.geohammer.util.Strings;
import javafx.scene.control.Button;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.math.CoordinatesMath;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;

/**
 * Layer responsible for grid visualization of GPR data.
 * <p>
 * This implementation supports two interpolation methods:
 * 1. Splines interpolation (default):
 * - Uses SplinesGridder2 with high tension (0.9999f)
 * - Suitable for dense, regular data
 * - Works well with small to medium cell sizes
 * <p>
 * 2. IDW (Inverse Distance Weighting):
 * - Better handling of large cell sizes
 * - Prevents artifacts in sparse data areas
 * - Adaptive search radius based on data density
 * - Configurable power parameter for distance weighting
 * <p>
 * The interpolation method can be selected through GriddingParamsSetted event.
 * For large cell sizes or irregular data distribution, IDW is recommended
 * to avoid interpolation artifacts.
 */
@Component
public final class GridLayer extends BaseLayer implements InitializingBean {

    private final Model model;

    private final TaskService taskService;

    private final GriddingService griddingService;

    private final ExecutorService executor;

    private final OptionPane optionPane;

    @SuppressWarnings({"NullAway.Init"})
    private ThrQueue q;

    @Nullable
    private CsvFile currentFile;

    @Nullable
    private GriddingParamsSetted currentParams;

    // Map to store gridding results for each file
    private final Map<File, GriddingResult> griddingResults = new ConcurrentHashMap<>();

    private final EventHandler<ActionEvent> showMapListener = new EventHandler<>() {
        @Override
        public void handle(ActionEvent event) {
            System.out.println("showMapListener: " + event);
            setActive(optionPane.getGridding().isSelected());
            getRepaintListener().repaint();
        }
    };

    public GridLayer(Model model, TaskService taskService, GriddingService griddingService,
                     ExecutorService executor, OptionPane optionPane) {
        this.model = model;
        this.taskService = taskService;
        this.griddingService = griddingService;
        this.executor = executor;
        this.optionPane = optionPane;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        setActive(optionPane.getGridding().isSelected());

        q = new ThrQueue(model) {
            protected void draw(BufferedImage backImg, MapField field) {
                Graphics2D g2 = (Graphics2D) backImg.getGraphics();
                g2.translate(backImg.getWidth() / 2, backImg.getHeight() / 2);
                drawOnMapField(g2, field);
            }

            public void ready() {
                getRepaintListener().repaint();
            }
        };

        optionPane.getGridding().addEventHandler(ActionEvent.ACTION, showMapListener);
    }

    @Override
    public void setSize(Dimension size) {
        q.setBackImgSize(size);
    }

    @Override
    public void draw(Graphics2D g2, MapField currentField) {
        if (currentField.getSceneCenter() == null || !isActive()) {
            return;
        }
        q.drawImgOnChangedField(g2, currentField, q.getFront());
    }

    public void drawOnMapField(Graphics2D g2, MapField field) {
        if (isActive()) {
            if (currentFile != null) {
                drawFileOnMapField(g2, field, currentFile);
            }
            setActive(optionPane.getGridding().isSelected());
        }
    }

    private static Color getColorForValue(double value, double min, double max) {
        value = Math.clamp(value, min, max);
        double normalized = (value - min) / (max - min);

        javafx.scene.paint.Color color = javafx.scene.paint.Color.hsb((1 - normalized) * 280, 0.8f, 0.8f);
        return new Color((float) color.getRed(), (float) color.getGreen(), (float) color.getBlue(), (float) color.getOpacity());
    }

    /**
     * Calculates hill-shading illumination value for a given point in the grid.
     *
     * @param gridData The grid data
     * @param x        X coordinate in the grid
     * @param y        Y coordinate in the grid
     * @param azimuth  Light source direction in degrees (0-360, 0=North, 90=East)
     * @param altitude Light source height in degrees (0-90, 0=horizon, 90=zenith)
     * @return Illumination value between 0.0 (dark) and 1.0 (bright)
     */
    private static double calculateHillShading(float[][] gridData, int x, int y, double azimuth, double altitude) {
        // Skip edge cells
        if (x <= 0 || y <= 0 || x >= gridData.length - 1 || y >= gridData[0].length - 1) {
            return 1.0; // Default illumination for edges
        }

        // Check for NaN values in the neighborhood
        if (Float.isNaN(gridData[x - 1][y]) || Float.isNaN(gridData[x + 1][y]) ||
                Float.isNaN(gridData[x][y - 1]) || Float.isNaN(gridData[x][y + 1])) {
            return 1.0; // Default illumination if neighbors have NaN
        }

        // Calculate slope components using central difference method
        double dzdx = (gridData[x + 1][y] - gridData[x - 1][y]) / 2.0;
        double dzdy = (gridData[x][y + 1] - gridData[x][y - 1]) / 2.0;

        // Calculate slope and aspect
        double slope = Math.atan(Math.sqrt(dzdx * dzdx + dzdy * dzdy));
        double aspect = Math.atan2(dzdy, dzdx);

        // Convert azimuth to radians and adjust to match aspect definition
        double azimuthRad = Math.toRadians(azimuth);

        // Convert altitude to radians
        double altitudeRad = Math.toRadians(altitude);

        // Calculate illumination using the hillshade formula
        double illumination = Math.cos(slope) * Math.sin(altitudeRad) +
                Math.sin(slope) * Math.cos(altitudeRad) *
                        Math.cos(azimuthRad - aspect);

        // Normalize illumination to [0, 1] range
        illumination = Math.max(0.0, illumination);

        return illumination;
    }

    /**
     * Applies hill-shading effect to a color.
     *
     * @param baseColor    The original color
     * @param illumination Illumination value between 0.0 (dark) and 1.0 (bright)
     * @param intensity    Intensity of the hill-shading effect (0.0-1.0)
     * @return The shaded color
     */
    private static Color applyHillShading(Color baseColor, double illumination, double intensity) {
        // Blend between original color and shaded color based on intensity
        float shadeFactor = (float) (1.0 - (1.0 - illumination) * intensity);

        // Convert int RGB values (0-255) to float (0.0-1.0), apply shading, and clamp to valid range
        float r = Math.max(0.0f, Math.min(1.0f, (baseColor.getRed() / 255.0f) * shadeFactor));
        float g = Math.max(0.0f, Math.min(1.0f, (baseColor.getGreen() / 255.0f) * shadeFactor));
        float b = Math.max(0.0f, Math.min(1.0f, (baseColor.getBlue() / 255.0f) * shadeFactor));

        // Keep the original alpha value
        return new Color(r, g, b, baseColor.getAlpha() / 255.0f);
    }

    /**
     * Draws the grid visualization for a given file on the map field.
     * <p>
     * The method performs the following steps:
     * 1. Collects data points from the file
     * 2. Creates a grid based on cell size
     * 3. Interpolates missing values using either:
     * - IDW interpolation (for large cell sizes)
     * - Splines interpolation (for small/medium cell sizes)
     * 4. Applies a low-pass filter to smooth the interpolated data
     * 5. Renders the filtered grid
     * <p>
     * The interpolation method is selected based on GriddingParamsSetted configuration.
     * <p>
     * For the current file, new minValue and maxValue are applied.
     * For other files, stored minValue and maxValue are used to ensure
     * they are displayed without changes from the previous application.
     */
    synchronized private void drawFileOnMapField(Graphics2D g2, MapField field, CsvFile csvFile) {
        var chart = model.getCsvChart(csvFile);
        if (chart.isEmpty()) {
            return;
        }

        // update gridding range for a target file
        String seriesName = chart.get().getSelectedSeriesName();
        if (griddingResults.get(csvFile.getFile()) instanceof GriddingResult gr && gr.sensor().equals(seriesName)) {
            var griddingRange = optionPane.getGriddingRange(csvFile, seriesName);
            griddingResults.put(csvFile.getFile(), gr.setValues(
                    (float) griddingRange.lowValue(),
                    (float) griddingRange.highValue(),
                    currentParams.isAnalyticSignalEnabled(),
                    currentParams.isHillShadingEnabled(),
                    currentParams.isSmoothingEnabled()));
        }

        // render gridding results, put current file's grid on top
        GriddingResult last = null;
        for (var e : griddingResults.entrySet()) {
            if (csvFile.getFile().equals(e.getKey())) {
                last = e.getValue();
            } else {
                drawGrid(g2, field, e.getValue());
            }
        }
        if (last != null) {
            drawGrid(g2, field, last);
        }
    }

    public Grid getCurrentGrid() {
        if (currentFile == null) {
            return null;
        }

        GriddingResult griddingResult = griddingResults.get(currentFile.getFile());
        if (griddingResult == null) {
            return null;
        }

        return getFilteredGrid(griddingResult);
    }

    private Grid getFilteredGrid(GriddingResult griddingResult) {
        Check.notNull(griddingResult);

        float[][] grid = griddingResult.smoothingEnabled()
                ? griddingResult.smoothedGridData()
                : griddingResult.gridData();

        float minValue = griddingResult.minValue();
        float maxValue = griddingResult.maxValue();

        if (griddingResult.analyticSignalEnabled()) {
            int gridWidth = grid.length;
            int gridHeight = grid[0].length;

            LatLon minLatLon = griddingResult.minLatLon();
            LatLon maxLatLon = griddingResult.maxLatLon();

            double lonStep = (maxLatLon.getLonDgr() - minLatLon.getLonDgr()) / gridWidth;
            double latStep = (maxLatLon.getLatDgr() - minLatLon.getLatDgr()) / gridHeight;

            double cellWidthMeters = CoordinatesMath.measure(0, 0, 0, lonStep);
            double cellHeightMeters = CoordinatesMath.measure(0, 0, latStep, 0);

            AnalyticSignalFilter filter = new AnalyticSignalFilter(
                    grid,
                    cellWidthMeters,
                    cellHeightMeters);
            AnalyticSignal signal = filter.evaluate();
            Range signalRange = signal.getRange(0.02);

            grid = signal.getMagnitudes();
            minValue = signalRange.getMin().floatValue();
            maxValue = signalRange.getMax().floatValue();
        }

        return new Grid(grid, minValue, maxValue,
                griddingResult.minLatLon(), griddingResult.maxLatLon());
    }

    private void drawGrid(Graphics2D g2, MapField field, GriddingResult gr) {
        Grid grid = getFilteredGrid(gr);

        var minLatLon = grid.minLatLon;
        var maxLatLon = grid.maxLatLon;

        int gridWidth = grid.values.length;
        int gridHeight = grid.values[0].length;

        double lonStep = (maxLatLon.getLonDgr() - minLatLon.getLonDgr()) / gridWidth;
        double latStep = (maxLatLon.getLatDgr() - minLatLon.getLatDgr()) / gridHeight;

        var minLatLonPoint = field.latLonToScreen(minLatLon);
        var nextLatLonPoint = field.latLonToScreen(new LatLon(minLatLon.getLatDgr() + latStep, minLatLon.getLonDgr() + lonStep));

        double cellWidth = Math.abs(minLatLonPoint.getX() - nextLatLonPoint.getX()) + 1; //3; //width / gridSizeX;
        double cellHeight = Math.abs(minLatLonPoint.getY() - nextLatLonPoint.getY()) + 1; //3; //height / gridSizeY;

        System.out.println("cellWidth = " + cellWidth + " cellHeight = " + cellHeight);

        // Log hill-shading parameters if enabled
        if (gr.hillShadingEnabled()) {
            System.out.println("Hill-shading enabled with azimuth=" + gr.hillShadingAzimuth() +
                    ", altitude=" + gr.hillShadingAltitude() + ", intensity=" + gr.hillShadingIntensity());
        }

        for (int i = 0; i < gridWidth; i++) {
            for (int j = 0; j < gridHeight; j++) {
                try {
                    float value = grid.values[i][j];
                    if (Float.isNaN(value)) {
                        continue;
                    }

                    // Get base color for the value
                    Color color = getColorForValue(value, grid.minValue, grid.maxValue);

                    // Apply hill-shading if enabled
                    if (gr.hillShadingEnabled()) {
                        // Calculate illumination for this cell
                        double illumination = calculateHillShading(
                                grid.values,
                                i,
                                j,
                                gr.hillShadingAzimuth(),
                                gr.hillShadingAltitude()
                        );

                        // Apply hill-shading to the color
                        color = applyHillShading(color, illumination, gr.hillShadingIntensity());
                    }

                    g2.setColor(color);

                    double lat = minLatLon.getLatDgr() + j * latStep;
                    double lon = minLatLon.getLonDgr() + i * lonStep;

                    var point = field.latLonToScreen(new LatLon(lat, lon));
                    g2.fillRect((int) point.getX(), (int) point.getY(), (int) cellWidth, (int) cellHeight);
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
            }
        }
    }

    @EventListener
    public void handleFileSelectedEvent(FileSelectedEvent event) {
        this.currentFile = event.getFile() instanceof CsvFile csvFile ? csvFile : null;
        if (this.currentFile != null) {
            submitDraw();
        }
    }

    @EventListener
    private void handleFileClosedEvent(FileClosedEvent event) {
        if (event.getSgyFile() instanceof CsvFile csvFile) {
            removeFromGriddingResults(csvFile);
            if (griddingResults.size() == 0) {
                submitDraw();
            }
        }
    }

    /**
     * Handles file rename events to invalidate cached gridding results.
     * <p>
     * When a file is renamed, the cached gridding results for that file
     * need to be invalidated to ensure that the grid is recomputed with
     * the new file path. This method removes the old gridding results
     * from the cache and triggers a grid recalculation.
     *
     * @param event the file rename event
     */
    @EventListener
    private void handleFileRenameEvent(FileRenameEvent event) {
        if (event.getSgyFile() instanceof CsvFile csvFile) {
            switchGriddingResult(csvFile, event.getOldFile());
        }
    }

    private synchronized void switchGriddingResult(CsvFile csvFile, File oldFile) {
        if (griddingResults.remove(oldFile) instanceof GriddingResult gr) {
            griddingResults.put(csvFile.getFile(), gr);
        }
    }

    private synchronized void removeFromGriddingResults(CsvFile csvFile) {
        griddingResults.remove(csvFile.getFile());
        if (model.getFileManager().getCsvFiles().isEmpty() && griddingResults.size() > 0) {
            griddingResults.clear();
        }
    }

    @EventListener
    private void somethingChanged(WhatChanged changed) {
        if (changed.isZoom() || changed.isWindowresized()
                || changed.isAdjusting()
                || changed.isMapscroll()
                //|| changed.isJustdraw()
                || changed.isGriddingRangeChanged()) {
            if (isActive()) {
                submitDraw();
            }
        } else if (changed.isCsvDataFiltered()) {
            submitGridding();
        }
    }

    /**
     * Handles grid parameter updates from the UI.
     * <p>
     * Updates include:
     * - Cell size for grid resolution
     * - Blanking distance for data filtering
     * - Interpolation method selection
     * - IDW-specific parameters (when IDW is selected)
     * <p>
     * Triggers grid recalculation only when explicitly requested through the UI.
     */
    @EventListener(GriddingParamsSetted.class)
    private void gridParamsSetted(GriddingParamsSetted griddingParams) {
        setActive(true);

        currentParams = griddingParams;

        // Only recalculate grid when explicitly requested through the UI
        // This is triggered by the "Apply" or "Apply to all" buttons
        if (griddingParams.getSource() instanceof Button) {
            submitGridding();
        } else {
            submitDraw();
        }
    }

    private void submitGridding() {
        CsvFile file = currentFile;
        if (file == null) {
            return;
        }
        SensorLineChart chart = model.getCsvChart(file).orElse(null);
        if (chart == null) {
            return;
        }
        String seriesName = chart.getSelectedSeriesName();
        if (Strings.isNullOrEmpty(seriesName)) {
            return;
        }
        GriddingParamsSetted params = currentParams;
        if (params == null) {
            return;
        }
        OptionPane.GriddingRange griddingRange = optionPane.getGriddingRange(file, seriesName);

        var future = executor.submit(() -> {
            optionPane.griddingProgress(true);
            return griddingService.runGridding(file, seriesName, params, griddingRange);
        });

        String taskName = "Gridding " + seriesName;
        if (file.getFile() != null) {
            taskName += ": " + file.getFile().getName();
        }
        taskService.registerTask(future, taskName)
                .whenComplete((results, throwable) -> {
            try {
                if (results != null) {
                    synchronized (this) {
                        griddingResults.putAll(results);
                    }
                    submitDraw();
                }
            } finally {
                optionPane.griddingProgress(false);
            }
        });
    }

    private void submitDraw() {
        q.add();
    }

    public record Grid(
            float[][] values,
            float minValue,
            float maxValue,
            LatLon minLatLon,
            LatLon maxLatLon
    ) {
    }
}
