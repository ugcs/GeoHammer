package com.ugcs.geohammer.chart.tool.projection;

import com.ugcs.geohammer.chart.tool.projection.math.ContrastCurve;
import com.ugcs.geohammer.chart.tool.projection.math.DbGain;
import com.ugcs.geohammer.chart.tool.projection.math.Normal;
import com.ugcs.geohammer.chart.tool.projection.model.Grid;
import com.ugcs.geohammer.chart.tool.projection.model.ProjectionResult;
import com.ugcs.geohammer.chart.tool.projection.model.TraceProfile;
import com.ugcs.geohammer.chart.tool.projection.model.ProjectionModel;
import com.ugcs.geohammer.chart.tool.projection.model.RenderOptions;
import com.ugcs.geohammer.chart.tool.projection.model.Viewport;
import com.ugcs.geohammer.service.palette.Palettes;
import com.ugcs.geohammer.service.palette.Spectrum;
import com.ugcs.geohammer.service.palette.SpectrumType;
import com.ugcs.geohammer.util.Nulls;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import java.util.List;

class ProjectionRenderer {

    private static final Color BACKGROUND_COLOR = Color.web("#333333");

    private static final Color NORMAL_COLOR = Color.POWDERBLUE;

    private static final double NORMAL_WIDTH = 0.5;

    private static final Color ANTENNA_COLOR = Color.SALMON;

    private static final Color TERRAIN_COLOR = Color.LINEN;

    private static final double TERRAIN_WIDTH = 1.0;

    private static final Color TERRAIN_FILTERED_COLOR = Color.YELLOWGREEN;

    private static final double TERRAIN_FILTERED_WIDTH = 2.0;

    private final ProjectionModel projectionModel;

    private final Canvas canvas;

    private final GraphicsContext g2;

    public ProjectionRenderer(
            ProjectionModel projectionModel,
            Canvas canvas) {
        this.projectionModel = projectionModel;
        this.canvas = canvas;
        this.g2 = canvas.getGraphicsContext2D();
    }

    public void draw() {
        clear();

        RenderOptions renderOptions = projectionModel.getRenderOptions();
        ProjectionResult projectionResult = projectionModel.getResult();
        TraceProfile traceProfile = projectionResult.getProfile();
        if (traceProfile != null) {
            Grid grid =  projectionResult.getGrid();
            if (grid != null) {
                if (renderOptions.isShowGrid()) {
                    drawGrid(traceProfile, grid);
                }
            }
            if (renderOptions.isShowNormals()) {
                drawNormals(traceProfile);
            }
            if (renderOptions.isShowTerrain()) {
                drawTerrain(traceProfile);
            }
            if (renderOptions.isShowOrigins()) {
                drawOrigins(traceProfile);
            }
        }
    }

    private int getColor(Spectrum spectrum, float value) {
        java.awt.Color color = spectrum.getColor(value);
        return 0xff000000
                | (color.getRed() << 16)
                | (color.getGreen() << 8)
                | color.getBlue();
    }

    private void drawGrid(TraceProfile traceProfile, Grid grid) {
        Viewport viewport = projectionModel.getViewport();

        int w = (int)canvas.getWidth();
        int h = (int)canvas.getHeight();
        if (w == 0 || h == 0) {
            return;
        }

        RenderOptions renderOptions = projectionModel.getRenderOptions();
        ContrastCurve contrastCurve = new ContrastCurve(renderOptions.getContrast());
        DbGain gainFunction = new DbGain(0, renderOptions.getMaxGain());
        float maxDepth = grid.getMaxDepth();

        SpectrumType spectrumType = renderOptions.getSpectrumType();
        Spectrum spectrum = Palettes.createSpectrum(spectrumType);
        int[] buffer = new int[w * h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                Point2D p = viewport.toWorld(new Point2D(x, y));
                Grid.Index gridIndex = grid.getIndex(p);
                if (gridIndex == null) {
                    continue;
                }

                Grid.Cell cell = grid.getCell(gridIndex);
                float v = cell.value();
                if (Float.isNaN(v)) {
                    continue;
                }
                float gain = gainFunction.getGain(maxDepth > 0 ? cell.depth() / maxDepth : 0);
                v *= gain;
                buffer[y * w + x] = getColor(spectrum, contrastCurve.map(v));
            }
        }

        WritableImage image = new WritableImage(w, h);
        image.getPixelWriter().setPixels(0, 0, w, h,
                PixelFormat.getIntArgbInstance(), buffer, 0, w);
        g2.drawImage(image, 0, 0);
    }

    private void drawOrigins(TraceProfile traceProfile) {
        List<Point2D> points = Nulls.toEmpty(traceProfile.getOrigins());
        drawPolyline(points, ANTENNA_COLOR);
    }

    private void drawTerrain(TraceProfile traceProfile) {
        List<Point2D> points = Nulls.toEmpty(traceProfile.getTerrain());
        drawPolyline(points, TERRAIN_COLOR, TERRAIN_WIDTH);
        List<Point2D> filteredPoints = Nulls.toEmpty(traceProfile.getTerrainFiltered());
        drawPolyline(filteredPoints, TERRAIN_FILTERED_COLOR, TERRAIN_FILTERED_WIDTH);
    }

    private void drawNormals(TraceProfile traceProfile) {
        int n = traceProfile.numTraces();
        for (int i = 0; i < n; i++) {
            Point2D p = traceProfile.getOrigin(i);
            Normal normal = traceProfile.getNormal(i);
            Point2D q = p.add(normal.unit().multiply(normal.length()));
            drawLine(p, q, NORMAL_COLOR, NORMAL_WIDTH);
        }
    }

    public void clear() {
        g2.setFill(BACKGROUND_COLOR);
        g2.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
    }

    public void drawCircle(Point2D p, double r, Color color) {
        Viewport viewport = projectionModel.getViewport();
        Point2D local = viewport.fromWorld(p);
        g2.setStroke(color);
        g2.strokeOval(local.getX() - r, local.getY() - r, 2 * r, 2 * r);
    }

    public void drawLine(Point2D p1, Point2D p2, Color color) {
        drawLine(p1, p2, color, g2.getLineWidth());
    }

    public void drawLine(Point2D p1, Point2D p2, Color color, double lineWidth) {
        Viewport viewport = projectionModel.getViewport();
        Point2D local1 = viewport.fromWorld(p1);
        Point2D local2 = viewport.fromWorld(p2);
        double lw = g2.getLineWidth();
        g2.setLineWidth(lineWidth);
        g2.setStroke(color);
        g2.strokeLine(local1.getX(), local1.getY(), local2.getX(), local2.getY());
        g2.setLineWidth(lw);
    }

    public void drawPolyline(List<Point2D> points, Color color) {
        drawPolyline(points, color, g2.getLineWidth());
    }

    public void drawPolyline(List<Point2D> points, Color color, double lineWidth) {
        Viewport viewport = projectionModel.getViewport();
        int n = points.size();
        if (n < 2) {
            return;
        }
        double[] xs = new double[n];
        double[] ys = new double[n];
        for (int i = 0; i < n; i++) {
            Point2D local = viewport.fromWorld(points.get(i));
            xs[i] = local.getX();
            ys[i] = local.getY();
        }
        double lw = g2.getLineWidth();
        g2.setLineWidth(lineWidth);
        g2.setStroke(color);
        g2.strokePolyline(xs, ys, n);
        g2.setLineWidth(lw);
    }
}
