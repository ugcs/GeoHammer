package com.ugcs.geohammer.chart.tool;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

import java.util.List;

class HistogramView extends Region {

    private static final int BINS = 18;

    private final Canvas canvas = new Canvas();

    private final double axisMinimum;

    private final double axisMaximum;

    private List<Double> values = List.of();

    HistogramView() {
        this(Double.NaN, Double.NaN);
    }

    HistogramView(double axisMinimum, double axisMaximum) {
        this.axisMinimum = axisMinimum;
        this.axisMaximum = axisMaximum;
        getChildren().add(canvas);
        setMinHeight(90);
        setPrefHeight(110);
    }

    void setValues(List<Double> values) {
        this.values = values;
        draw();
    }

    @Override
    protected void layoutChildren() {
        canvas.setWidth(getWidth());
        canvas.setHeight(getHeight());
        draw();
    }

    private void draw() {
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        if (width <= 0.0 || height <= 0.0) {
            return;
        }
        GraphicsContext graphics = canvas.getGraphicsContext2D();
        graphics.setFill(Color.rgb(245, 245, 245));
        graphics.fillRect(0, 0, width, height);
        if (values.isEmpty()) {
            graphics.setFill(Color.GRAY);
            graphics.fillText("No usable values", 8, height / 2.0);
            return;
        }

        double minimum = Double.isFinite(axisMinimum) ? axisMinimum : Double.POSITIVE_INFINITY;
        double maximum = Double.isFinite(axisMaximum) ? axisMaximum : Double.NEGATIVE_INFINITY;
        for (double value : values) {
            if (!Double.isFinite(axisMinimum)) {
                minimum = Math.min(minimum, value);
            }
            if (!Double.isFinite(axisMaximum)) {
                maximum = Math.max(maximum, value);
            }
        }
        if (maximum == minimum) {
            maximum = minimum + 1.0;
        }
        int[] bins = new int[BINS];
        for (double value : values) {
            int bin = Math.min((int) ((value - minimum) / (maximum - minimum) * BINS), BINS - 1);
            bins[Math.max(bin, 0)]++;
        }
        int maxCount = 1;
        for (int count : bins) {
            maxCount = Math.max(maxCount, count);
        }
        double barWidth = width / BINS;
        graphics.setFill(Color.rgb(69, 130, 180));
        for (int i = 0; i < BINS; i++) {
            double barHeight = (height - 20) * bins[i] / maxCount;
            graphics.fillRect(i * barWidth + 1, height - 16 - barHeight, Math.max(1, barWidth - 2), barHeight);
        }
        drawAxis(graphics, width, height, minimum, maximum);
    }

    private static void drawAxis(GraphicsContext graphics, double width, double height, double minimum, double maximum) {
        graphics.setFill(Color.GRAY);
        graphics.setStroke(Color.GRAY);
        for (int i = 0; i <= 4; i++) {
            double ratio = (double) i / 4.0;
            double x = ratio * width;
            double value = minimum + ratio * (maximum - minimum);
            graphics.strokeLine(x, height - 16, x, height - 13);
            String text = format(value);
            double textX = Math.clamp(x - text.length() * 3.5, 2, width - text.length() * 7 - 2);
            graphics.fillText(text, textX, height - 2);
        }
    }

    private static String format(double value) {
        return value >= 10.0 ? String.format("%.0f", value) : String.format("%.2f", value);
    }
}
