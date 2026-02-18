package com.ugcs.geohammer.chart.tool;

import com.ugcs.geohammer.AppContext;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.map.layer.GridLayer;
import com.ugcs.geohammer.model.Range;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
import com.ugcs.geohammer.model.event.GridUpdatedEvent;
import com.ugcs.geohammer.service.palette.Palette;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Formats;
import com.ugcs.geohammer.util.Ticks;
import com.ugcs.geohammer.view.Views;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class PaletteView {

    private static final String WINDOW_TITLE = "Palette";
    private static final String NO_DATA = "No data";

    // layout
    private static final double PADDING = 18;
    private static final double GRADIENT_GAP = 3;
    private static final double GRADIENT_HEIGHT = 10;
    private static final double X_AXIS_GAP = 5;
    private static final double X_AXIS_HEIGHT = 15;
    private static final double Y_AXIS_GAP = 10;
    private static final double Y_AXIS_WIDTH = 23;
	private static final double TICK_LENGTH = 3;

    // style
	private static final Color BACKGROUND_COLOR = Color.web("#494949");
	private static final Color CDF_COLOR = Color.web("#ccc");
    private static final double CDF_WIDTH = 1.25;

	private static final Color AXIS_COLOR = Color.web("#aaa");
    private static final Font AXIS_FONT = Font.font("Arial", 10);
    private static final int NUM_Y_AXIS_TICKS = 4;

    private static final int NUM_HISTOGRAM_BARS = 128;

	private final GridLayer gridLayer;

    private SgyFile selectedFile;

	@Nullable
	private Stage window;

	@Nullable
	private Canvas canvas;

    private volatile Palette palette;

    private volatile Histogram histogram;

	public PaletteView(GridLayer gridLayer) {
		this.gridLayer = gridLayer;
	}

	private Stage createWindow() {
		Stage window = new Stage();
		window.setTitle(WINDOW_TITLE);
		window.initOwner(AppContext.stage);
		window.initStyle(StageStyle.UTILITY);
		window.setResizable(true);
		window.setMinWidth(300);
		window.setMinHeight(200);

		canvas = new Canvas();
        StackPane root = new StackPane(canvas);

		canvas.widthProperty().bind(root.widthProperty());
		canvas.heightProperty().bind(root.heightProperty());
		canvas.widthProperty().addListener((observable, oldValue, newValue) -> updateView());
		canvas.heightProperty().addListener((observable, oldValue, newValue) -> updateView());

		Scene scene = new Scene(root, 500, 250);
		window.setScene(scene);

		window.setOnCloseRequest(event -> {
			event.consume();
			hide();
		});
		AppContext.stage.focusedProperty().addListener((observable, wasFocused, isFocused)
                -> window.setAlwaysOnTop(isFocused));

		return window;
	}

	public void show() {
		Platform.runLater(() -> {
			if (window == null) {
				window = createWindow();
			}
			window.show();
			window.toFront();
            updateView();
		});
	}

	public void hide() {
		if (window != null) {
            Platform.runLater(() -> window.hide());
		}
	}

	public void toggle() {
		if (isShowing()) {
			hide();
		} else {
			show();
		}
	}

	public boolean isShowing() {
		return window != null && window.isShowing();
	}

    public void update(SgyFile file) {
        GridLayer.Grid grid = file != null
                ? gridLayer.getGrid(file)
                : null;
        update(grid);
    }

    public void update(GridLayer.Grid grid) {
        if (grid != null) {
            histogram = Histogram.compute(
                    grid.values(),
                    grid.range(),
                    NUM_HISTOGRAM_BARS);
            palette = grid.palette();
        } else {
            histogram = null;
            palette = null;
        }
        updateView();
    }

    private void updateView() {
        if (isShowing()) {
            Platform.runLater(this::draw);
        }
    }

	private void draw() {
		if (canvas == null) {
			return;
		}

        Palette palette = this.palette;
        Histogram histogram = this.histogram;

        GraphicsContext g2 = canvas.getGraphicsContext2D();
		double w = canvas.getWidth();
		double h = canvas.getHeight();

        Rectangle2D canvasRect = new Rectangle2D(0, 0, w, h);

        clear(g2, canvasRect);

		if (palette == null || histogram == null) {
            drawNoData(g2, canvasRect);
			return;
		}

        Rectangle2D plotRect = new Rectangle2D(
                PADDING,
                PADDING,
                w - 2 * PADDING - Y_AXIS_GAP - Y_AXIS_WIDTH,
                h - 2 * PADDING - GRADIENT_GAP - GRADIENT_HEIGHT - X_AXIS_GAP - X_AXIS_HEIGHT);

		drawHistogram(g2, plotRect, histogram, palette);
		drawGridLines(g2, plotRect);
		drawCdf(g2, plotRect, histogram);

        Rectangle2D gradientRect = new Rectangle2D(
                plotRect.getMinX(),
                plotRect.getMaxY() + GRADIENT_GAP,
                plotRect.getWidth(),
                GRADIENT_HEIGHT);

        drawGradient(g2, gradientRect, palette);

        Rectangle2D xAxisRect = new Rectangle2D(
                plotRect.getMinX(),
                gradientRect.getMaxY() + X_AXIS_GAP,
                plotRect.getWidth(),
                X_AXIS_HEIGHT);

        drawXAxis(g2, xAxisRect, palette.getRange());

        Rectangle2D yAxisRect = new Rectangle2D(
                plotRect.getMaxX() + Y_AXIS_GAP,
                plotRect.getMinY(),
                Y_AXIS_WIDTH,
                plotRect.getHeight());

        drawYAxis(g2, yAxisRect);
	}

    private void clear(GraphicsContext g2, Rectangle2D rect) {
        g2.setFill(BACKGROUND_COLOR);
        g2.fillRect(rect.getMinX(), rect.getMinY(), rect.getWidth(), rect.getHeight());
    }

    private void drawNoData(GraphicsContext g2, Rectangle2D rect) {
        g2.setFill(AXIS_COLOR);
        g2.setFont(AXIS_FONT);
        g2.setTextAlign(TextAlignment.CENTER);
        g2.setTextBaseline(VPos.CENTER);

        double x = 0.5 * (rect.getMinX() + rect.getMaxX());
        double y = 0.5 * (rect.getMinY() + rect.getMaxY());
        g2.fillText(NO_DATA, x, y);
    }

	private void drawHistogram(GraphicsContext g2, Rectangle2D rect, Histogram histogram, Palette palette) {
        int n = histogram.numBars();
        if (n == 0) {
            return;
        }

        Range range = palette.getRange();
        double dx = rect.getWidth() / n;
        double dv = range.getWidth() / n;

        for (int i = 0; i < n; i++) {
			double barHeight = histogram.scaleBar(i, rect.getHeight());
            // value at the middle of the band
            double barValue = range.getMin() + (i + 0.5) * dv;
            java.awt.Color barColor = palette.getColor(barValue);

            g2.setFill(Views.fxColor(barColor));
            // fill with a small overlap
			g2.fillRect(rect.getMinX() + i * dx - 0.2, rect.getMaxY() - barHeight, dx + 0.4, barHeight);
		}
	}

	private void drawGridLines(GraphicsContext g2, Rectangle2D rect) {
		g2.setStroke(AXIS_COLOR);
		g2.setLineWidth(0.5);
		g2.setLineDashes(1, 4);

        for (int i = 1; i <= NUM_Y_AXIS_TICKS; i++) {
			double k = (double)i / NUM_Y_AXIS_TICKS;
			double y = rect.getMaxY() - k * rect.getHeight();
			g2.strokeLine(rect.getMinX(), y, rect.getMaxX(), y);
		}

        g2.setLineDashes((double[])null);
	}

	private void drawCdf(GraphicsContext g2, Rectangle2D rect, Histogram histogram) {
        int n = histogram.numBars();
        if (n == 0) {
            return;
        }

		g2.setStroke(CDF_COLOR);
		g2.setLineWidth(CDF_WIDTH);

        double dx = rect.getWidth() / n;

		double[] xs = new double[n + 1];
		double[] ys = new double[n + 1];
		int sum = 0;
        for (int i = 0; i <= n; i++) {
			xs[i] = rect.getMinX() + i * dx;
            double percentile = histogram.total() > 0
                    ? (double)sum / histogram.total()
                    : 0;
			ys[i] = rect.getMaxY() - percentile * rect.getHeight();
            if (i < n) {
                sum += histogram.bar(i);
            }
		}
		g2.strokePolyline(xs, ys, n + 1);
	}

	private void drawGradient(GraphicsContext g2, Rectangle2D rect, Palette palette) {
        int n = Math.min((int)rect.getWidth(), 512); // num color bands
        if (n == 0) {
            return;
        }

        Range range = palette.getRange();

        double dx = rect.getWidth() / n;
        double dv = range.getWidth() / n;

        for (int i = 0; i < n; i++) {
            // value at the middle of the band
            double value = range.getMin() + (i + 0.5) * dv;
            java.awt.Color color = palette.getColor(value);

            g2.setFill(Views.fxColor(color));
            // fill with a small overlap
			g2.fillRect(rect.getMinX() + i * dx, rect.getMinY(), dx + 0.4, rect.getHeight());
		}
	}

	private void drawXAxis(GraphicsContext g2, Rectangle2D rect, Range range) {
		g2.setStroke(AXIS_COLOR);
		g2.setLineWidth(0.5);

		g2.setFill(AXIS_COLOR);
		g2.setFont(AXIS_FONT);
		g2.setTextBaseline(VPos.TOP);

        List<Double> ticks = buildXTicks(rect, range);
		for (int i = 0; i < ticks.size(); i++) {
            double value = ticks.get(i);

            double k = range.getWidth() > 0
                    ? (value - range.getMin()) / range.getWidth()
                    : 0;
			double x = rect.getMinX() + k * rect.getWidth();

			g2.strokeLine(x, rect.getMinY(), x, rect.getMinY() + TICK_LENGTH);
            String label = Formats.prettyForRange(value, range.getMin(), range.getMax());
			TextAlignment alignment = TextAlignment.CENTER;
            if (i == 0) {
                alignment = TextAlignment.LEFT;
			} else if (i == ticks.size() - 1) {
                alignment = TextAlignment.RIGHT;
			}
            g2.setTextAlign(alignment);
			g2.fillText(label, x, rect.getMinY() + X_AXIS_GAP);
		}
	}

    private List<Double> buildXTicks(Rectangle2D rect, Range range) {
        int spacing = 120; // in pixels
        double tickSpacing = range.getWidth() * spacing / Math.max(rect.getWidth(), spacing);

        int numTicks = Math.max((int)(rect.getWidth() / spacing), 1);
        double tickUnit = Ticks.getPrettyTick(range.getMin(), range.getMax(), numTicks);

        List<Double> ticks = new ArrayList<>();
        ticks.add(range.getMin());
        if (tickUnit > 0) {
            double tick = Math.ceil(range.getMin() / tickUnit) * tickUnit;
            while (tick < range.getMax()) {
                if (tick - ticks.getLast() >= 0.5 * tickSpacing) {
                    ticks.add(tick);
                }
                tick += tickUnit;
            }
        }
        if (ticks.size() > 1 && range.getMax() - ticks.getLast() < 0.5 * tickSpacing) {
            ticks.removeLast();
        }
        ticks.add(range.getMax());
        return ticks;
    }

	private void drawYAxis(GraphicsContext g2, Rectangle2D rect) {
		g2.setFill(AXIS_COLOR);
		g2.setFont(AXIS_FONT);
		g2.setTextAlign(TextAlignment.LEFT);
		g2.setTextBaseline(VPos.CENTER);

		for (int i = 0; i <= NUM_Y_AXIS_TICKS; i++) {
			double k = (double)i / NUM_Y_AXIS_TICKS;
			double y = rect.getMaxY() - k * rect.getHeight();
			String label = (int)(k * 100) + "%";
			g2.fillText(label, rect.getMinX(), y);
		}
	}

    @EventListener
    protected void onFileSelected(FileSelectedEvent event) {
        selectedFile = event.getFile();
        update(event.getFile());
    }

    @EventListener
    protected void onGridUpdated(GridUpdatedEvent event) {
        if (Objects.equals(event.getFile(), selectedFile)) {
            update(event.getGrid());
        }
    }

    static class Histogram {

        private final int[] bars;

        private final int total;

        private final int max;

        private Histogram(int[] bars, int total, int max) {
            Check.notNull(bars);

            this.bars = bars;
            this.total = total;
            this.max = max;
        }

        public int numBars() {
            return bars.length;
        }

        public int bar(int i) {
            return bars[i];
        }

        public double scaleBar(int i, double maxHeight) {
            return max > 0 ? maxHeight / max * bars[i] : 0;
        }

        public int total() {
            return total;
        }

        public int max() {
            return max;
        }

        public static Histogram compute(float[][] grid, @NonNull Range range, int numBars) {
            if (grid == null || numBars <= 0) {
                return new Histogram(new int[0], 0, 0);
            }

            int[] bars = new int[numBars];
            double barWidth = range.getWidth() / numBars;
            for (float[] row : grid) {
                for (float value : row) {
                    if (Float.isNaN(value) || !range.contains(value)) {
                        continue;
                    }
                    int bar = barWidth != 0
                            ? (int) ((value - range.getMin()) / barWidth)
                            : 0;
                    // bar == n for d == range.max
                    bar = Math.clamp(bar, 0, numBars - 1);
                    bars[bar]++;
                }
            }
            int total = bars[0];
            int max = bars[0];
            for (int i = 1; i < numBars; i++) {
                total += bars[i];
                max = Math.max(max, bars[i]);
            }
            return new Histogram(bars, total, max);
        }
    }
}
