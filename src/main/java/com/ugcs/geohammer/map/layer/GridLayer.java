package com.ugcs.geohammer.map.layer;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;

import com.ugcs.geohammer.math.GaussianSmoothing;
import com.ugcs.geohammer.model.TemplateSeriesKey;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.map.RenderQueue;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.model.MapField;
import com.ugcs.geohammer.model.event.FileRenameEvent;
import com.ugcs.geohammer.model.event.FileClosedEvent;
import com.ugcs.geohammer.model.event.GridUpdatedEvent;
import com.ugcs.geohammer.service.gridding.GriddingFilter;
import com.ugcs.geohammer.service.gridding.GriddingResult;
import com.ugcs.geohammer.service.palette.Palette;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
import com.ugcs.geohammer.model.event.WhatChanged;
import com.ugcs.geohammer.math.AnalyticSignal;
import com.ugcs.geohammer.math.AnalyticSignalFilter;
import com.ugcs.geohammer.service.palette.Palettes;
import com.ugcs.geohammer.model.Range;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.SinglePendingExecutor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ugcs.geohammer.model.Model;

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
 */
@Component
public final class GridLayer extends BaseLayer {

    private static final double HILLSHADING_AZIMUTH = 180.0;

    private static final double HILLSHADING_ALTITUDE = 45.0;

    private static final double HILLSHADING_INTENSITY = 0.5;

    private static final Logger log = LoggerFactory.getLogger(GridLayer.class);

    private final Model model;

    private final ExecutorService executor;

    private final ConcurrentMap<SgyFile, SinglePendingExecutor> pendingExecutors = new ConcurrentHashMap<>();

    @SuppressWarnings({"NullAway.Init"})
    private RenderQueue q;

    @Nullable
    private SgyFile selectedFile;

    private final ConcurrentMap<File, GriddingResult> results = new ConcurrentHashMap<>();

    private final ConcurrentMap<TemplateSeriesKey, GriddingFilter> filters = new ConcurrentHashMap<>();

    private final ConcurrentMap<SgyFile, Grid> gridCache = new ConcurrentHashMap<>();

    public GridLayer(Model model, ExecutorService executor) {
        this.model = model;
        this.executor = executor;

        q = new RenderQueue(model) {
            public void draw(BufferedImage image, MapField field) {
                Graphics2D g2 = (Graphics2D) image.getGraphics();
                g2.translate(image.getWidth() / 2, image.getHeight() / 2);
                drawOnMapField(g2, field);
            }

            public void onReady() {
                getRepaintListener().repaint();
            }
        };
    }

    public boolean hasResult(SgyFile file) {
        return file != null && results.containsKey(file.getFile());
    }

    public GriddingResult getResult(SgyFile file) {
        if (file != null) {
            return results.get(file.getFile());
        }
        return null;
    }

    public void setResult(SgyFile file, GriddingResult result) {
        if (file != null) {
            results.put(file.getFile(), result);
            updateGrid(file, true);
        }
    }

    private void removeResult(SgyFile file) {
        if (file != null) {
            results.remove(file.getFile());
            updateGrid(file);
        }
    }

    private void moveResult(SgyFile file, File oldFile) {
        GriddingResult result = results.get(oldFile);
        if (result != null) {
            results.put(file.getFile(), result);
            results.remove(oldFile, result); // only removes if still points to same value
            updateGrid(file);
        }
    }

    public GriddingFilter getFilter(SgyFile file, String seriesName) {
        TemplateSeriesKey templateSeries = TemplateSeriesKey.ofSeries(file, seriesName);
        if (templateSeries != null) {
            return filters.get(templateSeries);
        }
        return null;
    }

    public void setFilter(SgyFile file, String seriesName, GriddingFilter filter) {
        TemplateSeriesKey templateSeries = TemplateSeriesKey.ofSeries(file, seriesName);
        if (templateSeries != null) {
            filters.put(templateSeries, filter);
            updateGrid(file);
        }
    }

    @Override
    public void setSize(Dimension size) {
        q.setRenderSize(size);
    }

    @Override
    public void draw(Graphics2D g2, MapField currentField) {
        if (currentField.getSceneCenter() == null || !isActive()) {
            return;
        }
        q.drawWithTransform(g2, currentField, q.getLastFrame());
    }

    public void drawOnMapField(Graphics2D g2, MapField field) {
        if (!isActive()) {
            return;
        }

        SgyFile last = selectedFile; // to draw on top
        for (SgyFile file : model.getFileManager().getFiles()) {
            if (!Objects.equals(file, last) && hasResult(file)) {
                drawGrid(g2, field, file);
            }
        }
        if (hasResult(last)) {
            drawGrid(g2, field, last);
        }
    }

    private void drawGrid(Graphics2D g2, MapField field, SgyFile file) {
        Grid grid = getGrid(file);
        if (grid == null) {
            return;
        }

        var minLatLon = grid.minLatLon;
        var maxLatLon = grid.maxLatLon;

        int gridWidth = grid.values.length;
        int gridHeight = gridWidth > 0 ? grid.values[0].length : 0;
        if (gridWidth == 0 || gridHeight == 0) {
            return;
        }

        double lonStep = (maxLatLon.getLonDgr() - minLatLon.getLonDgr()) / gridWidth;
        double latStep = (maxLatLon.getLatDgr() - minLatLon.getLatDgr()) / gridHeight;

        var minLatLonPoint = field.latLonToScreen(minLatLon);
        var nextLatLonPoint = field.latLonToScreen(new LatLon(minLatLon.getLatDgr() + latStep, minLatLon.getLonDgr() + lonStep));

        double cellWidth = Math.abs(minLatLonPoint.getX() - nextLatLonPoint.getX()) + 1; //3; //width / gridSizeX;
        double cellHeight = Math.abs(minLatLonPoint.getY() - nextLatLonPoint.getY()) + 1; //3; //height / gridSizeY;

        for (int i = 0; i < gridWidth; i++) {
            for (int j = 0; j < gridHeight; j++) {
                try {
                    float value = grid.values[i][j];
                    if (Float.isNaN(value)) {
                        continue;
                    }

                    Color color = grid.palette.getColor(value);

                    // Apply hill-shading if enabled
                    if (grid.filter.hillShading()) {
                        // Calculate illumination for this cell
                        double illumination = calculateHillShading(
                                grid.values,
                                i,
                                j,
                                HILLSHADING_AZIMUTH,
                                HILLSHADING_ALTITUDE
                        );

                        // Apply hill-shading to the color
                        color = applyHillShading(
                                color,
                                illumination,
                                HILLSHADING_INTENSITY
                        );
                    }

                    g2.setColor(color);

                    double lat = minLatLon.getLatDgr() + j * latStep;
                    double lon = minLatLon.getLonDgr() + i * lonStep;

                    var point = field.latLonToScreen(new LatLon(lat, lon));
                    g2.fillRect((int) point.getX(), (int) point.getY(), (int) cellWidth, (int) cellHeight);
                } catch (Exception e) {
                    log.error("Error", e);
                }
            }
        }
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

    public Grid getCurrentGrid() {
        return getGrid(selectedFile);
    }

    public Grid getGrid(SgyFile file) {
        if (file != null) {
            return gridCache.get(file);
        }
        return null;
    }

    private void updateGrid(SgyFile file) {
        updateGrid(file, false);
    }

    private void updateGrid(SgyFile file, boolean ignoreCached) {
        Check.notNull(file);

        GriddingResult result = getResult(file);
        GriddingFilter filter = result != null
                ? getFilter(file, result.seriesName())
                : null;
        if (result == null || filter == null) {
            gridCache.remove(file);
            model.publishEvent(new GridUpdatedEvent(this, file, null));
            return;
        }

        SinglePendingExecutor pendingExecutor = pendingExecutors
                .computeIfAbsent(file, key -> new SinglePendingExecutor(executor));

        pendingExecutor.submit(() -> {
            // get cached grid
            Grid grid = gridCache.get(file);

            float[][] values;
            float[] sortedValues;
            Range range;
            boolean updateValues = ignoreCached || shouldUpdateValues(grid, filter);

            if (updateValues) {
                values = result.grid();
                range = filter.range();
                if (filter.smoothing()) {
                    GaussianSmoothing smoothing = new GaussianSmoothing();
                    values = smoothing.apply(values);
                }
                if (filter.analyticSignal()) {
                    AnalyticSignalFilter analyticSignalFilter = new AnalyticSignalFilter(
                            values,
                            result.minLatLon(),
                            result.maxLatLon());
                    AnalyticSignal signal = analyticSignalFilter.evaluate();
                    values = signal.getMagnitudes();
                    range = signal.getRange(0.02);
                }
                sortedValues = sortGridValues(values);
            } else {
                values = grid.values();
                sortedValues = grid.sortedValues();
                if (filter.analyticSignal()) {
                    range = grid.range();
                } else {
                    range = filter.range();
                }
            }

            Palette palette;
            boolean updatePalette = ignoreCached || shouldUpdatePalette(grid, filter);

            if (updateValues || updatePalette) {
                palette = Palettes.create(
                        filter.paletteType(),
                        filter.spectrumType(),
                        sortedValues,
                        range);
            } else {
                palette = grid.palette();
            }

            grid = new Grid(
                    values,
                    sortedValues,
                    result.minLatLon(),
                    result.maxLatLon(),
                    range,
                    palette,
                    filter
            );
            gridCache.put(file, grid);
            submitDraw();
            model.publishEvent(new GridUpdatedEvent(this, file, grid));
        });
    }

    private boolean shouldUpdateValues(Grid grid, GriddingFilter filter) {
        return grid == null
                || grid.filter() == null
                || !Objects.equals(grid.filter().smoothing(), filter.smoothing())
                || !Objects.equals(grid.filter().analyticSignal(), filter.analyticSignal());
    }

    private boolean shouldUpdatePalette(Grid grid, GriddingFilter filter) {
        return grid == null
                || grid.filter() == null
                || grid.filter().paletteType() != filter.paletteType()
                || grid.filter().spectrumType() != filter.spectrumType()
                || !Objects.equals(grid.filter().range(), filter.range());
    }

    private float[] sortGridValues(float[][] grid) {
        int n = 0;
        for (float[] row : grid) {
            for (float value : row) {
                if (!Float.isNaN(value)) {
                    n++;
                }
            }
        }
        float[] values = new float[n];
        int i = 0;
        for (float[] row : grid) {
            for (float value : row) {
                if (!Float.isNaN(value)) {
                    values[i++] = value;
                }
            }
        }
        Arrays.sort(values);
        return values;
    }

    @EventListener
    public void onFileSelected(FileSelectedEvent event) {
        selectedFile = event.getFile();
        if (selectedFile != null) {
            submitDraw();
        }
    }

    @EventListener
    private void onFileClosed(FileClosedEvent event) {
        SgyFile file = event.getFile();
        if (file != null) {
            removeResult(file);
            pendingExecutors.remove(file);
            submitDraw();
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
    private void onFileRenamed(FileRenameEvent event) {
        if (event.getSgyFile() != null && event.getOldFile() != null) {
            moveResult(event.getSgyFile(), event.getOldFile());
        }
    }

    @EventListener
    private void onChanged(WhatChanged changed) {
        if (changed.isZoom() || changed.isWindowresized()
                || changed.isAdjusting()
                || changed.isMapscroll()) {
            if (isActive()) {
                submitDraw();
            }
        }
    }

    public void submitDraw() {
        q.submit();
    }

    public record Grid(
            float[][] values,
            float[] sortedValues,
            LatLon minLatLon,
            LatLon maxLatLon,
            Range range,
            Palette palette,
            GriddingFilter filter
    ) {
    }
}
