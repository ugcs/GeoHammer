package com.ugcs.geohammer.chart.tool.projection;

import com.ugcs.geohammer.AppContext;
import com.ugcs.geohammer.chart.tool.projection.math.ContrastCurve;
import com.ugcs.geohammer.chart.tool.projection.math.DbGain;
import com.ugcs.geohammer.chart.tool.projection.math.Polyline;
import com.ugcs.geohammer.chart.tool.projection.model.Grid;
import com.ugcs.geohammer.chart.tool.projection.model.ProjectionModel;
import com.ugcs.geohammer.chart.tool.projection.model.ProjectionResult;
import com.ugcs.geohammer.chart.tool.projection.model.RenderOptions;
import com.ugcs.geohammer.chart.tool.projection.model.TraceProfile;
import com.ugcs.geohammer.chart.tool.projection.model.TraceRay;
import com.ugcs.geohammer.chart.tool.projection.model.Viewport;
import com.ugcs.geohammer.service.palette.Palettes;
import com.ugcs.geohammer.service.palette.Spectrum;
import com.ugcs.geohammer.service.palette.SpectrumType;
import com.ugcs.geohammer.util.Formats;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Ticks;
import com.ugcs.geohammer.view.Colors;
import com.ugcs.geohammer.view.style.Theme;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.util.List;
import java.util.function.Function;

class ProjectionRenderer {

    private static final Color NORMAL_COLOR = Color.web("#8899AA");

    private static final double NORMAL_WIDTH = 0.5;

    private static final Color ANTENNA_COLOR = Color.web("#72DDF7");

    private static final double ANTENNA_WIDTH = 1.8;

    private static final Color RAW_TERRAIN_COLOR = Color.WHEAT;

    private static final double RAW_TERRAIN_WIDTH = 1.0;

    private static final Color TERRAIN_COLOR = Color.web("#FB7185");

    private static final double TERRAIN_WIDTH = 1.8;

    private static final Font AXIS_FONT = Font.font(10);

    private static final double AXIS_X_MARGIN = 40;

    private static final double AXIS_Y_MARGIN = 34;

    private static final double AXIS_TICK_LENGTH = 4;

    private static final double AXIS_LABEL_GAP = 4;

    private static final double AXIS_TICK_SPACING = 50;

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
                drawGrid(grid);
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
            drawAxes();
        }
    }

    private int getColor(Spectrum spectrum, float value) {
        java.awt.Color color = spectrum.getColor(value);
        return 0xff000000
                | (color.getRed() << 16)
                | (color.getGreen() << 8)
                | color.getBlue();
    }

    private void drawGrid(Grid grid) {
        Viewport viewport = projectionModel.getViewport();

        int w = (int)canvas.getWidth();
        int h = (int)canvas.getHeight();
        if (w == 0 || h == 0) {
            return;
        }

        RenderOptions renderOptions = projectionModel.getRenderOptions();
        ContrastCurve contrastCurve = new ContrastCurve(100 * renderOptions.getContrast());
        DbGain gainFunction = new DbGain(0, renderOptions.getMaxGain());
        float maxDepth = grid.getMaxDepth();

        SpectrumType spectrumType = renderOptions.getSpectrumType();
        Spectrum spectrum = Palettes.createSpectrum(spectrumType);
        int[] buffer = new int[w * h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                Point2D point = viewport.toWorld(new Point2D(x, y));
                Grid.Index cellIndex = grid.getIndex(point);
                if (cellIndex == null) {
                    continue;
                }
                Grid.Cell cell = grid.getCell(cellIndex);
                if (cell == null) {
                    continue;
                }
                float value = cell.getValue();
                if (Float.isNaN(value)) {
                    continue;
                }
                float gain = gainFunction.getGain(maxDepth > 0 ? cell.getDepth() / maxDepth : 0);
                value *= gain;
                buffer[y * w + x] = getColor(spectrum, contrastCurve.map(value));
            }
        }

        WritableImage image = new WritableImage(w, h);
        image.getPixelWriter().setPixels(0, 0, w, h,
                PixelFormat.getIntArgbInstance(), buffer, 0, w);
        g2.drawImage(image, 0, 0);
    }

    private void drawOrigins(TraceProfile traceProfile) {
        List<TraceRay> rays = Nulls.toEmpty(traceProfile.getRays());
        drawPolyline(rays, TraceRay::origin, ANTENNA_COLOR, ANTENNA_WIDTH);
    }

    private void drawTerrain(TraceProfile traceProfile) {
        List<Point2D> rawTerrainPoints = Nulls.toEmpty(traceProfile.getRawTerrain());
        drawPolyline(rawTerrainPoints, RAW_TERRAIN_COLOR, RAW_TERRAIN_WIDTH);

        Polyline terrain = traceProfile.getTerrain();
        if (terrain != null) {
            List<Point2D> terrainPoints = Nulls.toEmpty(terrain.getPoints());
            drawPolyline(terrainPoints, TERRAIN_COLOR, TERRAIN_WIDTH);
        }
    }

    private void drawNormals(TraceProfile traceProfile) {
        int n = traceProfile.numTraces();
        for (int i = 0; i < n; i++) {
            TraceRay ray = traceProfile.getRay(i);
            if (ray.soilOrigin() != null) {
                drawLine(ray.origin(), ray.soilOrigin(), NORMAL_COLOR, NORMAL_WIDTH);
            }
        }
    }

    private Rectangle2D getPlotArea() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w == 0 || h == 0) {
            return Rectangle2D.EMPTY;
        }

        double marginX = Math.min(AXIS_X_MARGIN, w / 2);
        double marginY = Math.min(AXIS_Y_MARGIN, h / 2);
        return new Rectangle2D(
                marginX,
                marginY,
                w - 2 * marginX,
                h - 2 * marginY);
    }

    private void drawAxes() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w == 0 || h == 0) {
            return;
        }

        Theme theme = AppContext.getTheme();
        Color strokeColor = theme.strokeColor();
        Color outlineColor = Colors.opaque(strokeColor.invert(), 0.5f);

        g2.save();
        try {
            g2.setEffect(createOutlineEffect(outlineColor, 4, 0.5));
            g2.setStroke(strokeColor);
            g2.setFill(strokeColor);
            g2.setFont(AXIS_FONT);
            g2.setLineWidth(0.5);

            Rectangle2D plot = getPlotArea();
            drawXAxis(plot);
            drawYAxis(plot);
        } finally {
            g2.restore();
        }
    }

    private void drawXAxis(Rectangle2D plot) {
        Point2D axisOrigin = projectionModel.getRenderOptions().getAxisOrigin();

        Viewport viewport = projectionModel.getViewport();
        Point2D worldMin = viewport.toWorld(new Point2D(plot.getMinX(), plot.getMaxY()));
        Point2D worldMax = viewport.toWorld(new Point2D(plot.getMaxX(), plot.getMinY()));
        double minX = worldMin.getX() - axisOrigin.getX();
        double maxX = worldMax.getX() - axisOrigin.getX();

        g2.strokeLine(plot.getMinX(), plot.getMaxY(), plot.getMaxX(), plot.getMaxY());
        g2.strokeLine(plot.getMaxX(), plot.getMaxY() - AXIS_TICK_LENGTH, plot.getMaxX(), plot.getMaxY() + AXIS_TICK_LENGTH);

        g2.setTextBaseline(VPos.TOP);
        g2.setTextAlign(TextAlignment.CENTER);

        int numXTicks = Math.max((int)(plot.getWidth() / AXIS_TICK_SPACING), 1);
        double xTick = Ticks.getPrettyTick(minX, maxX, numXTicks);
        if (xTick > 0) {
            double x = Math.ceil(minX / xTick) * xTick;
            while (x <= maxX) {
                double viewportX = viewport.fromWorld(new Point2D(x + axisOrigin.getX(), 0)).getX();
                g2.strokeLine(viewportX, plot.getMaxY(), viewportX, plot.getMaxY() + AXIS_TICK_LENGTH);
                String label = Formats.prettyForRange(x, minX, maxX);
                g2.fillText(label, viewportX, plot.getMaxY() + AXIS_TICK_LENGTH + AXIS_LABEL_GAP);
                x += xTick;
            }
        }
    }

    private void drawYAxis(Rectangle2D plot) {
        Point2D axisOrigin = projectionModel.getRenderOptions().getAxisOrigin();

        Viewport viewport = projectionModel.getViewport();
        Point2D worldMin = viewport.toWorld(new Point2D(plot.getMinX(), plot.getMaxY()));
        Point2D worldMax = viewport.toWorld(new Point2D(plot.getMaxX(), plot.getMinY()));
        double minY = worldMin.getY() - axisOrigin.getY();
        double maxY = worldMax.getY() - axisOrigin.getY();

        g2.strokeLine(plot.getMinX(), plot.getMinY(), plot.getMinX(), plot.getMaxY());
        g2.strokeLine(plot.getMinX() - AXIS_TICK_LENGTH, plot.getMinY(), plot.getMinX() + AXIS_TICK_LENGTH, plot.getMinY());

        g2.setTextBaseline(VPos.CENTER);
        g2.setTextAlign(TextAlignment.RIGHT);

        int numYTicks = Math.max((int)(plot.getHeight() / AXIS_TICK_SPACING), 1);
        double yTick = Ticks.getPrettyTick(minY, maxY, numYTicks);
        if (yTick > 0) {
            double y = Math.ceil(minY / yTick) * yTick;
            while (y <= maxY) {
                double viewportY = viewport.fromWorld(new Point2D(0, y + axisOrigin.getY())).getY();
                g2.strokeLine(plot.getMinX() - AXIS_TICK_LENGTH, viewportY, plot.getMinX(), viewportY);
                String label = Formats.prettyForRange(y, minY, maxY);
                g2.fillText(label, plot.getMinX() - AXIS_TICK_LENGTH - AXIS_LABEL_GAP, viewportY);
                y += yTick;
            }
        }
    }

    private DropShadow createOutlineEffect(Color color, double radius, double spread) {
        DropShadow effect = new DropShadow();
        effect.setRadius(radius);
        effect.setOffsetX(0);
        effect.setOffsetY(0);
        effect.setSpread(spread);
        effect.setColor(color);
        return effect;
    }

    public void clear() {
        g2.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
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

    public void drawPolyline(List<Point2D> points, Color color, double lineWidth) {
        drawPolyline(points, p -> p, color, lineWidth);
    }

    public <T> void drawPolyline(List<T> items, Function<T, Point2D> toPoint, Color color, double lineWidth) {
        Viewport viewport = projectionModel.getViewport();
        int n = items.size();
        if (n < 2) {
            return;
        }
        double[] xs = new double[n];
        double[] ys = new double[n];
        for (int i = 0; i < n; i++) {
            T item = items.get(i);
            Point2D point = toPoint.apply(item);
            Point2D local = viewport.fromWorld(point);
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
