package com.ugcs.geohammer.chart.tool;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

import java.util.List;

class HistogramView extends Region {

    private static final int BINS = 18;

    private final Canvas canvas = new Canvas();

    private List<Double> values = List.of();

    HistogramView() {
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

        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        for (double value : values) {
            minimum = Math.min(minimum, value);
            maximum = Math.max(maximum, value);
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
        graphics.setFill(Color.GRAY);
        graphics.fillText(format(minimum), 2, height - 2);
        String maximumText = format(maximum);
        graphics.fillText(maximumText, Math.max(2, width - maximumText.length() * 7), height - 2);
    }

    private static String format(double value) {
        return value >= 10.0 ? String.format("%.0f", value) : String.format("%.2f", value);
    }
}
