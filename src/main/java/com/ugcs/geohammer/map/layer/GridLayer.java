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
import com.ugcs.geohammer.service.gridding.GriddingParams;
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

    private static final Logger log = LoggerFactory.getLogger(GridLayer.class);

    // smoothing sigma relative to the blanking distance, keeps smoothing
    // the same in meters for any cell size
    private static final double SMOOTHING_SIGMA_FACTOR = 0.38;

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

        int[] cellX = new int[gridWidth + 1];
        for (int i = 0; i <= gridWidth; i++) {
            double lon = minLatLon.getLonDgr() + i * lonStep;
            cellX[i] = (int) Math.round(field.latLonToScreen(new LatLon(minLatLon.getLatDgr(), lon)).getX());
        }
        int[] cellY = new int[gridHeight + 1];
        for (int j = 0; j <= gridHeight; j++) {
            double lat = minLatLon.getLatDgr() + j * latStep;
            cellY[j] = (int) Math.round(field.latLonToScreen(new LatLon(lat, minLatLon.getLonDgr())).getY());
        }

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
                        double illumination = HillShading.computeIllumination(
                                grid.values,
                                i,
                                j,
                                grid.zFactor
                        );

                        // Apply hill-shading to the color
                        color = HillShading.apply(
                                color,
                                illumination
                        );
                    }

                    g2.setColor(color);

                    // cellY descend with the row index, as screen y grows southward
                    g2.fillRect(cellX[i], cellY[j + 1],
                            cellX[i + 1] - cellX[i], cellY[j] - cellY[j + 1]);
                } catch (Exception e) {
                    log.error("Error", e);
                }
            }
        }
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
            double zFactor;
            boolean updateValues = ignoreCached || shouldUpdateValues(grid, filter);

            if (updateValues) {
                values = result.grid();
                if (filter.smoothing()) {
                    GriddingParams params = result.params();
                    GaussianSmoothing smoothing = new GaussianSmoothing(
                            SMOOTHING_SIGMA_FACTOR * params.blankingDistance() / params.cellSize());
                    values = smoothing.apply(values);
                }
                if (filter.analyticSignal()) {
                    AnalyticSignalFilter analyticSignalFilter = new AnalyticSignalFilter(
                            values,
                            result.minLatLon(),
                            result.maxLatLon());
                    values = analyticSignalFilter.evaluate().magnitudes();
                }
                sortedValues = sortGridValues(values);
                range = filter.analyticSignal()
                        ? AnalyticSignal.getRange(sortedValues, 0.02)
                        : filter.range();
                zFactor = HillShading.getZFactor(values, HillShading.MEAN_SLOPE);
            } else {
                values = grid.values();
                sortedValues = grid.sortedValues();
                zFactor = grid.zFactor();
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
                    result.seriesName(),
                    values,
                    sortedValues,
                    result.minLatLon(),
                    result.maxLatLon(),
                    range,
                    palette,
                    zFactor,
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
            String seriesName,
            float[][] values,
            float[] sortedValues,
            LatLon minLatLon,
            LatLon maxLatLon,
            Range range,
            Palette palette,
            double zFactor,
            GriddingFilter filter
    ) {
    }
}
