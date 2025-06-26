package com.ugcs.gprvisualizer.app;

import com.github.thecoldwine.sigrun.common.ext.GprFile;
import com.github.thecoldwine.sigrun.common.ext.LatLon;
import com.github.thecoldwine.sigrun.common.ext.MapField;
import com.github.thecoldwine.sigrun.common.ext.SampleNormalizer;
import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.github.thecoldwine.sigrun.common.ext.Trace;
import com.github.thecoldwine.sigrun.common.ext.TraceFile;
import com.github.thecoldwine.sigrun.common.ext.TraceKey;
import com.ugcs.gprvisualizer.app.auxcontrol.Positional;
import com.ugcs.gprvisualizer.app.meta.SampleRange;
import com.ugcs.gprvisualizer.app.parcers.GeoData;
import com.ugcs.gprvisualizer.event.WhatChanged;
import com.ugcs.gprvisualizer.gpr.Model;
import com.ugcs.gprvisualizer.utils.AuxElements;
import com.ugcs.gprvisualizer.utils.Check;
import com.ugcs.gprvisualizer.utils.Nulls;
import javafx.geometry.Point2D;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class TraceTransform {

    private final Model model;
    private ApplicationEventPublisher eventPublisher;

    public TraceTransform(Model model, ApplicationEventPublisher eventPublisher) {
        this.model = model;
        this.eventPublisher = eventPublisher;
    }

    public void cropLines(SgyFile file, MapField field, List<Point2D> cropArea) {
        Check.notNull(file);

        Chart chart = model.getFileChart(file);
        TraceKey selectedTrace = null;
        if (chart != null) {
            selectedTrace = model.getSelectedTrace(chart);
        }

        // positional element indices should be updated
        // on trace manipulations
        Map<Integer, List<Positional>> elements = AuxElements
                .getPositionalElements(file.getAuxElements());

        boolean prevInside = false;
        int prevLineIndex = 0;
        int cutLineIndex = 0;
        boolean cutIndexUsed = false;

        Iterator<GeoData> it = file.getGeoData().iterator();
        int traceIndex = 0;
        int numRemoved = 0;
        while (it.hasNext()) {
            GeoData value = it.next();

            boolean inside = isGeoDataInsideSelection(field, cropArea, value);
            int lineIndex = value.getLineIndexOrDefault();

            if (!inside && prevInside || lineIndex != prevLineIndex) {
                if (cutIndexUsed) {
                    cutLineIndex++;
                    cutIndexUsed = false;
                }
            }
            if (inside) {
                value.setLineIndex(cutLineIndex);
                cutIndexUsed = true;
                // offset elements
                for (Positional element : Nulls.toEmpty(elements.get(traceIndex))) {
                    element.offset(-numRemoved);
                }
                // offset selection
                if (selectedTrace != null && selectedTrace.getIndex() == traceIndex) {
                    selectedTrace.offset(-numRemoved);
                }
            } else {
                it.remove();
                numRemoved++;
                // remove elements
                for (Positional element : Nulls.toEmpty(elements.get(traceIndex))) {
                    file.getAuxElements().remove(element);
                }
                // clear selection on chart
                if (selectedTrace != null && selectedTrace.getIndex() == traceIndex) {
                    model.clearSelectedTrace(chart);
                }
            }

            prevInside = inside;
            prevLineIndex = lineIndex;
            traceIndex++;
        }

        onFileTracesUpdated(file);
    }

    public boolean isStartOfLine(SgyFile file, int traceIndex) {
        Check.notNull(file);
        Check.condition(traceIndex >= 0);

        if (traceIndex == 0) {
            return true;
        }
        List<GeoData> values = file.getGeoData();
        return values.get(traceIndex).getLineIndexOrDefault()
                != values.get(traceIndex - 1).getLineIndexOrDefault();
    }

    public void splitLine(SgyFile file, int splitIndex) {
        Check.notNull(file);
        Check.condition(splitIndex >= 0);

        List<GeoData> values = file.getGeoData();

        // shift lines after a split trace
        int splitLine = values.get(splitIndex).getLineIndexOrDefault();
        Set<Integer> linesToShift = new HashSet<>();
        linesToShift.add(splitLine);
        for (int i = splitIndex; i < values.size(); i++) {
            GeoData value = values.get(i);
            int lineIndex = value.getLineIndexOrDefault();
            if (linesToShift.contains(lineIndex)) {
                value.setLineIndex(lineIndex + 1);
                linesToShift.add(lineIndex + 1);
            }
        }

        onFileTracesUpdated(file);
    }

    public void removeLine(SgyFile file, int lineIndex) {
        Check.notNull(file);

        Chart chart = model.getFileChart(file);
        TraceKey selectedTrace = null;
        if (chart != null) {
            selectedTrace = model.getSelectedTrace(chart);
        }

        // positional element indices should be updated
        // on trace manipulations
        Map<Integer, List<Positional>> elements = AuxElements
                .getPositionalElements(file.getAuxElements());

        Iterator<GeoData> it = file.getGeoData().iterator();
        int traceIndex = 0;
        int numRemoved = 0;
        while (it.hasNext()) {
            GeoData value = it.next();
            int valueLineIndex = value.getLineIndexOrDefault();
            if (valueLineIndex != lineIndex) {
                if (valueLineIndex > lineIndex) {
                    value.setLineIndex(valueLineIndex - 1);
                }
                // offset elements
                for (Positional element : Nulls.toEmpty(elements.get(traceIndex))) {
                    element.offset(-numRemoved);
                }
                // offset selection
                if (selectedTrace != null && selectedTrace.getIndex() == traceIndex) {
                    selectedTrace.offset(-numRemoved);
                }
            } else {
                it.remove();
                numRemoved++;
                // remove elements
                for (Positional element : Nulls.toEmpty(elements.get(traceIndex))) {
                    file.getAuxElements().remove(element);
                }
                // clear selection on chart
                if (selectedTrace != null && selectedTrace.getIndex() == traceIndex) {
                    model.clearSelectedTrace(chart);
                }
            }

            traceIndex++;
        }

        onFileTracesUpdated(file);
    }

    private void onFileTracesUpdated(SgyFile file) {
        Check.notNull(file);

        file.setUnsaved(true);

        // update aux elements in model and charts,
        // as they are copies of elements stored in a file
        model.updateAuxElements();

        // reload chart
        Chart chart = model.getFileChart(file);
        if (chart != null) {
            chart.reload();
        }

        eventPublisher.publishEvent(new WhatChanged(this,
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

        // revert normalization
        if (file instanceof GprFile gprFile) {
            SampleNormalizer normalizer = gprFile.getSampleNormalizer();
            normalizer.back(file.getTraces());
        }

        length = Math.max(1, length);
        SampleRange sampleRange = new SampleRange(offset, offset + length);
        for (Trace trace : file.getTraces()) {
            trace.setSampleRange(sampleRange);
        }

        // re-normalize samples
        if (file instanceof GprFile gprFile) {
            SampleNormalizer normalizer = gprFile.getSampleNormalizer();
            normalizer.normalize(file.getTraces());
        }

        onFileTracesUpdated(file);
    }
}
