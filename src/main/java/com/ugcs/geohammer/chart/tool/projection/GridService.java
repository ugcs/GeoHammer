package com.ugcs.geohammer.chart.tool.projection;

import com.ugcs.geohammer.chart.tool.projection.math.Normal;
import com.ugcs.geohammer.chart.tool.projection.model.Grid;
import com.ugcs.geohammer.chart.tool.projection.model.GridSample;
import com.ugcs.geohammer.chart.tool.projection.model.GridOptions;
import com.ugcs.geohammer.chart.tool.projection.model.ProjectionModel;
import com.ugcs.geohammer.chart.tool.projection.model.TraceProfile;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Nulls;
import javafx.geometry.Point2D;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GridService {

    private final ProjectionModel projectionModel;

    public GridService(ProjectionModel projectionModel) {
        this.projectionModel = projectionModel;
    }

    public Grid buildGrid(TraceProfile traceProfile) {
        if (traceProfile == null) {
            return null;
        }

        GridOptions gridOptions = projectionModel.getGridOptions();
        double cellWidth = gridOptions.getCellWidth();
        double cellHeight = gridOptions.getCellHeight();

        List<Point2D> boundingPolyline = getBoundingPolyline(traceProfile, gridOptions.isCropAir());
        Grid grid = new Grid(boundingPolyline, cellWidth, cellHeight);

        fillGrid(grid, traceProfile);
        if (gridOptions.isInterpolateGrid()) {
            interpolateGrid(grid);
        }
        grid.updateMaxDepth();
        return grid;
    }

    public List<Point2D> getBoundingPolyline(TraceProfile traceProfile, boolean cropAir) {
        Check.notNull(traceProfile);

        int numTraces = traceProfile.numTraces();
        int numSamples = traceProfile.numSamples();

        List<Point2D> polyline = new ArrayList<>(2 * numTraces);
        // top
        List<Point2D> top = cropAir
                ? traceProfile.getTerrainFiltered()
                : traceProfile.getOrigins();
        polyline.addAll(top);
        // bottom
        List<Point2D> bottom = new ArrayList<>(numTraces);
        for (int i = 0; i < numTraces; i++) {
            Point2D origin = traceProfile.getOrigin(i);
            Normal normal = traceProfile.getNormal(i);
            double airGap = normal.length();

            // last sample
            double ln = traceProfile.getSampleDepth(numSamples - 1, airGap);
            Point2D pn = origin.add(normal.unit().multiply(ln));
            if (bottom.isEmpty() || bottom.getLast().getX() < pn.getX()) {
                bottom.add(pn);
            }
        }
        Collections.reverse(bottom);
        polyline.addAll(bottom);
        return polyline;
    }

    private void fillGrid(Grid grid, TraceProfile traceProfile) {
        Check.notNull(grid);
        Check.notNull(traceProfile);

        int numTraces = traceProfile.numTraces();
        int numSamples = traceProfile.numSamples();

        Map<Grid.Index, List<GridSample>> gridSamples
                = new HashMap<>(grid.getWidth() * grid.getHeight());

        for (int i = 0; i < numTraces; i++) {
            Point2D p = traceProfile.getOrigin(i);
            Normal normal = traceProfile.getNormal(i);

            for (int j = 0; j < numSamples; j++) {
                float value = traceProfile.getValue(i, j);
                if (Float.isNaN(value)) {
                    continue;
                }

                double depth = traceProfile.getSampleDepth(j, normal.length());
                double depthBelowSurface = Math.max(0, depth - normal.length());
                Point2D position = p.add(normal.unit().multiply(depth));
                if (!grid.inBounds(position)) {
                    continue;
                }
                Grid.Index gridIndex = grid.getIndex(position);
                if (gridIndex == null) {
                    continue;
                }

                GridSample gridSample = new GridSample(position, (float)depthBelowSurface, normal, value);
                gridSamples.computeIfAbsent(gridIndex, key -> new ArrayList<>()).add(gridSample);
            }
        }

        for (Map.Entry<Grid.Index, List<GridSample>> entry : gridSamples.entrySet()) {
            Grid.Cell cell = sample(entry.getValue());
            grid.setCell(entry.getKey(), cell);
        }
    }

    private Grid.Cell sample(List<GridSample> samples) {
        if (Nulls.isNullOrEmpty(samples)) {
            return Grid.EMPTY_CELL;
        }

        GridOptions gridOptions = projectionModel.getGridOptions();
        return switch (gridOptions.getSamplingMethod()) {
            case MAX -> sampleMax(samples);
            case AVERAGE -> sampleAverage(samples);
            case DEPTH_WEIGHTED -> sampleDepthWeighted(samples);
        };
    }

    private Grid.Cell sampleMax(List<GridSample> samples) {
        float maxValue = 0;
        float maxDepth = 0;
        for (GridSample sample : samples) {
            float value = sample.value();
            if (!Float.isNaN(value) && Math.abs(value) > Math.abs(maxValue)) {
                maxValue = value;
                maxDepth = sample.depth();
            }
        }
        return new Grid.Cell(maxValue, maxDepth);
    }

    private Grid.Cell sampleAverage(List<GridSample> samples) {
        float valueSum = 0;
        float depthSum = 0;
        int count = 0;
        for (GridSample sample : samples) {
            float value = sample.value();
            if (!Float.isNaN(value)) {
                valueSum += value;
                depthSum += sample.depth();
                count++;
            }
        }
        return count > 0
                ? new Grid.Cell(valueSum / count, depthSum / count)
                : Grid.EMPTY_CELL;
    }

    // weight = 1 / (1 + depth^2), favors shallow direct measurements
    private Grid.Cell sampleDepthWeighted(List<GridSample> samples) {
        float weightedValueSum = 0;
        float weightedDepthSum = 0;
        float weightSum = 0;
        for (GridSample sample : samples) {
            float value = sample.value();
            if (Float.isNaN(value)) {
                continue;
            }
            float depth = sample.depth();
            float weight = (float)(1.0 / (1.0 + depth * depth));
            weightedValueSum += weight * value;
            weightedDepthSum += weight * depth;
            weightSum += weight;
        }
        return weightSum > 0
                ? new Grid.Cell(weightedValueSum / weightSum, weightedDepthSum / weightSum)
                : Grid.EMPTY_CELL;
    }

    private void interpolateGrid(Grid grid) {
        Check.notNull(grid);

        grid.interpolate();
    }
}
