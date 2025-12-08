package com.ugcs.geohammer.service;

import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.model.MapField;
import com.ugcs.geohammer.format.meta.MetaFile;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.model.TraceKey;
import com.ugcs.geohammer.chart.Chart;
import com.ugcs.geohammer.model.element.PositionalObject;
import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.model.undo.UndoModel;
import com.ugcs.geohammer.model.event.WhatChanged;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.util.AuxElements;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.util.Nulls;
import javafx.geometry.Point2D;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class TraceTransform {

    private final Model model;
    private final UndoModel undoModel;

    public TraceTransform(Model model, UndoModel undoModel) {
        this.model = model;
        this.undoModel = undoModel;
    }

    public void cropLines(Collection<SgyFile> files, MapField field, List<Point2D> cropArea) {
        Check.notNull(files);

        // filter files by crop area intersection
        files = files.stream()
                .filter(file -> hasValuesInArea(file, field, cropArea))
                .toList();

        if (files.isEmpty()) {
            return;
        }

        undoModel.saveSnapshot(files);

        for (SgyFile file : files) {
            cropLines(file, field, cropArea);
        }
    }

    private boolean hasValuesInArea(SgyFile file, MapField field, List<Point2D> area) {
        if (file == null) {
            return false;
        }
        for (GeoData value : Nulls.toEmpty(file.getGeoData())) {
            if (isGeoDataInsideSelection(field, area, value)) {
                return true;
            }
        }
        return false;
    }

    private void cropLines(SgyFile file, MapField field, List<Point2D> cropArea) {
        Check.notNull(file);

        Chart chart = model.getChart(file);
        TraceKey selectedTrace = null;
        if (chart != null) {
            selectedTrace = model.getSelectedTrace(chart);
        }

        // positional element indices should be updated
        // on trace manipulations
        Map<Integer, List<PositionalObject>> elements = AuxElements
                .getPositionalElements(file.getAuxElements());

        // all elements that should be removed
        Set<PositionalObject> toRemove = new HashSet<>();

        boolean prevInside = false;
        int prevLineIndex = 0;
        int cutLineIndex = 0;
        boolean cutIndexUsed = false;

        List<GeoData> values = file.getGeoData();
        int numRemoved = 0;

        for (int i = 0; i < values.size(); i++) {
            GeoData value = values.get(i);

            boolean inside = isGeoDataInsideSelection(field, cropArea, value);
            int lineIndex = value.getLineOrDefault(0);

            if (!inside && prevInside || lineIndex != prevLineIndex) {
                if (cutIndexUsed) {
                    cutLineIndex++;
                    cutIndexUsed = false;
                }
            }
            if (inside) {
                value.setLine(cutLineIndex);
                cutIndexUsed = true;
                if (numRemoved > 0) {
                    values.set(i - numRemoved, value);
                    // offset elements
                    for (PositionalObject element : Nulls.toEmpty(elements.get(i))) {
                        element.offset(-numRemoved);
                    }
                    // offset selection
                    if (selectedTrace != null && selectedTrace.getIndex() == i) {
                        selectedTrace.offset(-numRemoved);
                    }
                }
            } else {
                numRemoved++;
                // collect elements to remove
                toRemove.addAll(Nulls.toEmpty(elements.get(i)));
                // clear selection on chart
                if (selectedTrace != null && selectedTrace.getIndex() == i) {
                    model.clearSelectedTrace(chart);
                }
            }

            prevInside = inside;
            prevLineIndex = lineIndex;
        }

        if (numRemoved > 0) {
            // clear tail
            values.subList(values.size() - numRemoved, values.size()).clear();
        }

        // remove elements
        file.getAuxElements().removeAll(toRemove);

        onFileTracesUpdated(file);
    }

    public boolean isStartOfLine(SgyFile file, int traceIndex) {
        Check.notNull(file);
        Check.condition(traceIndex >= 0);

        if (traceIndex == 0) {
            return true;
        }
        List<GeoData> values = file.getGeoData();
        return !Objects.equals(
                values.get(traceIndex).getLine(),
                values.get(traceIndex - 1).getLine());
    }

    public void splitLine(SgyFile file, int splitIndex) {
        Check.notNull(file);
        Check.condition(splitIndex >= 0);

        undoModel.saveSnapshot(file);

        List<GeoData> values = file.getGeoData();

        // shift lines after a split trace
        int splitLine = values.get(splitIndex).getLineOrDefault(0);
        Set<Integer> linesToShift = new HashSet<>();
        linesToShift.add(splitLine);
        for (int i = splitIndex; i < values.size(); i++) {
            GeoData value = values.get(i);
            int lineIndex = value.getLineOrDefault(0);
            if (linesToShift.contains(lineIndex)) {
                value.setLine(lineIndex + 1);
                linesToShift.add(lineIndex + 1);
            }
        }

        onFileTracesUpdated(file);
    }

    public void removeLine(SgyFile file, int lineIndex) {
        Check.notNull(file);

        undoModel.saveSnapshot(file);

        Chart chart = model.getChart(file);
        TraceKey selectedTrace = null;
        if (chart != null) {
            selectedTrace = model.getSelectedTrace(chart);
        }

        // positional element indices should be updated
        // on trace manipulations
        Map<Integer, List<PositionalObject>> elements = AuxElements
                .getPositionalElements(file.getAuxElements());

        // all elements that should be removed
        Set<PositionalObject> toRemove = new HashSet<>();

        List<GeoData> values = file.getGeoData();
        int numRemoved = 0;

        for (int i = 0; i < values.size(); i++) {
            GeoData value = values.get(i);
            int valueLineIndex = value.getLineOrDefault(0);
            if (valueLineIndex != lineIndex) {
                if (valueLineIndex > lineIndex) {
                    value.setLine(valueLineIndex - 1);
                }
                if (numRemoved > 0) {
                    values.set(i - numRemoved, value);
                    // offset elements
                    for (PositionalObject element : Nulls.toEmpty(elements.get(i))) {
                        element.offset(-numRemoved);
                    }
                    // offset selection
                    if (selectedTrace != null && selectedTrace.getIndex() == i) {
                        selectedTrace.offset(-numRemoved);
                    }
                }
            } else {
                numRemoved++;
                // collect elements to remove
                toRemove.addAll(Nulls.toEmpty(elements.get(i)));
                // clear selection on chart
                if (selectedTrace != null && selectedTrace.getIndex() == i) {
                    model.clearSelectedTrace(chart);
                }
            }
        }

        if (numRemoved > 0) {
            // clear tail
            values.subList(values.size() - numRemoved, values.size()).clear();
        }

        // remove elements
        file.getAuxElements().removeAll(toRemove);

        onFileTracesUpdated(file);
    }

    private void onFileTracesUpdated(SgyFile file) {
        Check.notNull(file);

        file.setUnsaved(true);
        file.tracesChanged();

        // update aux elements in model and charts,
        // as they are copies of elements stored in a file
        model.updateAuxElements();

        // reload chart
        Chart chart = model.getChart(file);
        if (chart != null) {
            chart.reload();
        }

        model.publishEvent(new WhatChanged(this,
                WhatChanged.Change.traceCut));
    }

    private boolean isGeoDataInsideSelection(MapField field, List<Point2D> border, GeoData geoData) {
        return isInsideSelection(field, border, new LatLon(geoData.getLatitude(), geoData.getLongitude()));
    }

    private boolean isInsideSelection(MapField field, List<Point2D> border, LatLon ll) {
        Point2D p = field.latLonToScreen(ll);
        return isPointInside(p, border);
    }

    private boolean isPointInside(Point2D point, List<Point2D> border) {
        boolean result = false;
        for (int i = 0; i < border.size(); i++) {
            Point2D pt1 = border.get(i);
            Point2D pt2 = border.get((i + 1) % border.size());

            if ((pt1.getY() > point.getY()) != (pt2.getY() > point.getY())
                    && (point.getX()
                    < (pt2.getX() - pt1.getX())
                    * (point.getY() - pt1.getY())
                    / (pt2.getY() - pt1.getY())
                    + pt1.getX())) {
                result = !result;
            }
        }

        return result;
    }

    public void cropGprSamples(TraceFile file, int offset, int length) {
        Check.notNull(file);

        MetaFile metaFile = file.getMetaFile();
        if (metaFile == null) {
            // operation requires meta file
            return;
        }

        undoModel.saveSnapshot(file);

        length = Math.max(1, length);
        IndexRange sampleRange = new IndexRange(offset, offset + length);
        IndexRange currentSampleRange = metaFile.getSampleRange();
        if (currentSampleRange != null) {
            sampleRange = currentSampleRange.subRange(sampleRange);
        }

        metaFile.setSampleRange(sampleRange);
        file.syncMeta();

        onFileTracesUpdated(file);
    }
}
