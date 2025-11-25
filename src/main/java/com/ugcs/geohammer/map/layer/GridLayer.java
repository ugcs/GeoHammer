package com.ugcs.geohammer.map.layer;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.ugcs.geohammer.model.TemplateSeriesKey;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.map.ThrQueue;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.model.MapField;
import com.ugcs.geohammer.model.event.FileRenameEvent;
import com.ugcs.geohammer.model.event.FileClosedEvent;
import com.ugcs.geohammer.service.gridding.GriddingFilter;
import com.ugcs.geohammer.service.gridding.GriddingResult;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
import com.ugcs.geohammer.model.event.WhatChanged;
import com.ugcs.geohammer.math.AnalyticSignal;
import com.ugcs.geohammer.math.AnalyticSignalFilter;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.model.Range;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.math.CoordinatesMath;

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

    private final Model model;

    @SuppressWarnings({"NullAway.Init"})
    private ThrQueue q;

    @Nullable
    private SgyFile selectedFile;

    private final Map<File, GriddingResult> results = new HashMap<>();

    private final Map<TemplateSeriesKey, GriddingFilter> filters = new HashMap<>();

    public GridLayer(Model model) {
        this.model = model;

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
    }

    public synchronized boolean hasResult(SgyFile file) {
        return file != null && results.containsKey(file.getFile());
    }

    public synchronized GriddingResult getResult(SgyFile file) {
        if (file != null) {
            return results.get(file.getFile());
        }
        return null;
    }

    public synchronized void setResult(SgyFile file, GriddingResult result) {
        if (file != null) {
            results.put(file.getFile(), result);
        }
    }

    private synchronized void removeResult(SgyFile file) {
        if (file != null) {
            results.remove(file.getFile());
        }
    }

    private synchronized void moveResult(SgyFile file, File oldFile) {
        GriddingResult result = results.remove(oldFile);
        if (result != null) {
            results.put(file.getFile(), result);
        }
    }

    public synchronized GriddingFilter getFilter(SgyFile file, String seriesName) {
        TemplateSeriesKey templateSeries = TemplateSeriesKey.ofSeries(file, seriesName);
        if (templateSeries != null) {
            return filters.get(templateSeries);
        }
        return null;
    }

    public synchronized void setFilter(SgyFile file, String seriesName, GriddingFilter filter) {
        TemplateSeriesKey templateSeries = TemplateSeriesKey.ofSeries(file, seriesName);
        if (templateSeries != null) {
            filters.put(templateSeries, filter);
        }
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
        if (!isActive()) {
            return;
        }

        // TODO draw only files of the same template
        SgyFile last = selectedFile; // to draw on top
        for (SgyFile file : model.getFileManager().getFiles()) {
            if (!Objects.equals(file, last) && hasResult(file)) {
                drawFileOnMapField(g2, field, file);
            }
        }
        if (hasResult(last)) {
            drawFileOnMapField(g2, field, last);
        }
    }

    /**
     * Draws the grid visualization for a given file on the map field.
     */
    synchronized private void drawFileOnMapField(Graphics2D g2, MapField field, SgyFile file) {
        // show last result for the file
        GriddingResult result = getResult(file);
        if (result == null) {
            return;
        }
        GriddingFilter filter = getFilter(file, result.seriesName());
        if (filter == null) {
            return;
        }
        drawGrid(g2, field, result, filter);
    }

    private void drawGrid(Graphics2D g2, MapField field, GriddingResult result, GriddingFilter filter) {
        Grid grid = getFilteredGrid(result, filter);

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
                    if (filter.hillShading()) {
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
                    System.out.println(e.getMessage());
                }
            }
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

    public Grid getCurrentGrid() {
        SgyFile file = selectedFile;
        if (file == null) {
            return null;
        }
        GriddingResult result = getResult(file);
        if (result == null) {
            return null;
        }
        GriddingFilter filter = getFilter(file, result.seriesName());
        if (filter == null) {
            return null;
        }
        return getFilteredGrid(result, filter);
    }

    private Grid getFilteredGrid(GriddingResult result, GriddingFilter filter) {
        Check.notNull(result);

        float[][] grid = filter.smoothing()
                ? result.smoothedGridData()
                : result.gridData();

        double minValue = filter.range().getMin();
        double maxValue = filter.range().getMax();

        if (filter.analyticSignal()) {
            int gridWidth = grid.length;
            int gridHeight = grid[0].length;

            LatLon minLatLon = result.minLatLon();
            LatLon maxLatLon = result.maxLatLon();

            double lonStep = (maxLatLon.getLonDgr() - minLatLon.getLonDgr()) / gridWidth;
            double latStep = (maxLatLon.getLatDgr() - minLatLon.getLatDgr()) / gridHeight;

            double cellWidthMeters = CoordinatesMath.measure(0, 0, 0, lonStep);
            double cellHeightMeters = CoordinatesMath.measure(0, 0, latStep, 0);

            AnalyticSignalFilter analyticSignalFilter = new AnalyticSignalFilter(
                    grid,
                    cellWidthMeters,
                    cellHeightMeters);
            AnalyticSignal signal = analyticSignalFilter.evaluate();
            Range signalRange = signal.getRange(0.02);

            grid = signal.getMagnitudes();
            minValue = signalRange.getMin();
            maxValue = signalRange.getMax();
        }

        return new Grid(
                grid,
                (float)minValue,
                (float)maxValue,
                result.minLatLon(),
                result.maxLatLon()
        );
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
            removeResult(event.getFile());
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
