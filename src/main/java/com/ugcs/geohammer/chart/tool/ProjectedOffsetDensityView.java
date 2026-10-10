package com.ugcs.geohammer.chart.tool;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

import java.util.List;

class ProjectedOffsetDensityView extends Region {

    private final Canvas canvas = new Canvas();

    private List<Double> positions = List.of();

    private List<Double> densityValues = List.of();

    private double previewSpacing = Double.NaN;

    ProjectedOffsetDensityView() {
        getChildren().add(canvas);
        setMinHeight(100);
        setPrefHeight(130);
    }

    void setDensity(List<Double> positions, List<Double> densityValues) {
        this.positions = positions;
        this.densityValues = densityValues;
        previewSpacing = Double.NaN;
        draw();
    }

    void setPreviewSpacing(double previewSpacing) {
        this.previewSpacing = previewSpacing;
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
        if (positions.isEmpty() || positions.size() != densityValues.size()) {
            graphics.setFill(Color.GRAY);
            graphics.fillText("No tie-line offsets", 8, height / 2.0);
            return;
        }

        double minimum = positions.getFirst();
        double maximum = positions.getLast();
        double range = Math.max(maximum - minimum, 1.0);
        double maximumDensity = 0.0;
        for (double density : densityValues) {
            maximumDensity = Math.max(maximumDensity, density);
        }
        maximumDensity = Math.max(maximumDensity, 1.0);

        double top = 8;
        double bottom = height - 18;
        drawDensity(graphics, width, top, bottom, minimum, range, maximumDensity);
        drawPreviewPicks(graphics, width, top, bottom, minimum, maximum, range);
        drawAxis(graphics, width, height, range);
    }

    private void drawDensity(GraphicsContext graphics, double width, double top, double bottom, double minimum,
                             double range, double maximumDensity) {
        graphics.setFill(Color.rgb(69, 130, 180));
        for (int i = 0; i < positions.size(); i++) {
            double x = (positions.get(i) - minimum) / range * width;
            double nextX = i + 1 < positions.size()
                    ? (positions.get(i + 1) - minimum) / range * width
                    : width;
            double barHeight = (bottom - top) * densityValues.get(i) / maximumDensity;
            graphics.fillRect(x, bottom - barHeight, Math.max(1.0, nextX - x), barHeight);
        }
    }

    private void drawPreviewPicks(GraphicsContext graphics, double width, double top, double bottom, double minimum,
                                  double maximum, double range) {
        if (!Double.isFinite(previewSpacing) || previewSpacing <= 0.0) {
            return;
        }
        double origin = findBestPickOrigin(minimum, maximum);
        graphics.setStroke(Color.rgb(221, 132, 32));
        graphics.setLineWidth(1.5);
        for (double pick = origin; pick <= maximum; pick += previewSpacing) {
            double x = (pick - minimum) / range * width;
            graphics.strokeLine(x, top, x, bottom);
        }
    }

    private double findBestPickOrigin(double minimum, double maximum) {
        double bestOrigin = minimum;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (double candidate : positions) {
            double score = 0.0;
            for (double pick = candidate; pick <= maximum; pick += previewSpacing) {
                score += densityAt(pick);
            }
            for (double pick = candidate - previewSpacing; pick >= minimum; pick -= previewSpacing) {
                score += densityAt(pick);
            }
            if (score > bestScore) {
                bestScore = score;
                bestOrigin = candidate;
            }
        }
        while (bestOrigin - previewSpacing >= minimum) {
            bestOrigin -= previewSpacing;
        }
        return bestOrigin;
    }

    private double densityAt(double position) {
        double binWidth = positions.size() > 1 ? positions.get(1) - positions.getFirst() : 1.0;
        int index = (int) Math.round((position - positions.getFirst()) / binWidth);
        return index >= 0 && index < densityValues.size() ? densityValues.get(index) : 0.0;
    }

    private static void drawAxis(GraphicsContext graphics, double width, double height, double range) {
        graphics.setFill(Color.GRAY);
        for (int i = 0; i <= 4; i++) {
            double ratio = (double) i / 4.0;
            double value = ratio * range;
            String text = format(value);
            double x = Math.clamp(ratio * width - text.length() * 3.5, 2, width - text.length() * 7 - 2);
            graphics.fillText(text, x, height - 2);
        }
    }

    private static String format(double value) {
        return value >= 100.0 ? String.format("%.0f", value) : String.format("%.1f", value);
    }
}
