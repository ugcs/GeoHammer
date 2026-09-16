package com.ugcs.geohammer.format.meta;

import com.google.gson.annotations.Expose;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.model.LineSchema;
import com.ugcs.geohammer.model.Range;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Nulls;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;

public class MetaDocument {

    @Expose
    private IndexRange sampleRange;

    @Expose
    private List<Line> lines;

    @Expose
    private List<Mark> marks;

    @Expose
    private Double contrast;

    @Expose
    private Boolean backgroundRemoved;

    @Expose
    private IndexRange depthRange;

    @Deprecated
    @Expose(serialize = false)
    private Range amplitudeRange;

    public IndexRange getSampleRange() {
        return sampleRange;
    }

    public void setSampleRange(IndexRange sampleRange) {
        this.sampleRange = sampleRange;
    }

    public List<Line> getLines() {
        return lines;
    }

    public void setLines(List<Line> lines) {
        this.lines = lines;
    }

    public List<Mark> getMarks() {
        return marks;
    }

    public void setMarks(List<Mark> marks) {
        this.marks = marks;
    }

    public Double getContrast() {
        return contrast;
    }

    public void setContrast(Double contrast) {
        this.contrast = contrast;
    }

    public Boolean getBackgroundRemoved() {
        return backgroundRemoved;
    }

    public void setBackgroundRemoved(Boolean backgroundRemoved) {
        this.backgroundRemoved = backgroundRemoved;
    }

    public @Nullable IndexRange getDepthRange() {
        if (depthRange != null) {
            return depthRange;
        }
        if (amplitudeRange != null) {
            return new IndexRange((int) amplitudeRange.getMin(), (int) amplitudeRange.getMax());
        }
        return null;
    }

    public void setDepthRange(@Nullable IndexRange depthRange) {
        this.depthRange = depthRange;
    }

    public static @Nullable MetaDocument of(@Nullable Meta meta) {
        if (meta == null) {
            return null;
        }

        MetaDocument metaDocument = new MetaDocument();
        metaDocument.setSampleRange(meta.getSampleRange());
        metaDocument.setContrast(meta.getContrast());
        metaDocument.setBackgroundRemoved(meta.getBackgroundRemoved());
        metaDocument.setDepthRange(meta.getDepthRange());
        metaDocument.setLines(linesOf(meta));
        metaDocument.setMarks(marksOf(meta));
        return metaDocument;
    }

    private static List<Line> linesOf(Meta meta) {
        Check.notNull(meta);

        List<TraceGeoData> metaValues = Nulls.toEmpty(meta.getValues());
        NavigableMap<Integer, IndexRange> lineRanges = LineSchema.getLineRanges(metaValues);

        List<Line> lines = new ArrayList<>();
        for (Map.Entry<Integer, IndexRange> e : lineRanges.entrySet()) {
            Integer lineIndex = e.getKey();
            IndexRange lineRange = e.getValue();

            TraceGeoData lineStart = metaValues.get(lineRange.from());
            TraceGeoData lineEnd = metaValues.get(lineRange.to() - 1);

            Line line = new Line();
            line.setLineIndex(lineIndex);
            line.setFrom(lineStart.getTraceIndex());
            line.setTo(lineEnd.getTraceIndex() + 1); // exclusive
            lines.add(line);
        }
        return lines;
    }

    private static List<Mark> marksOf(Meta meta) {
        Check.notNull(meta);

        List<TraceGeoData> metaValues = Nulls.toEmpty(meta.getValues());
        Set<Integer> metaMarks = Nulls.toEmpty(meta.getMarks());

        List<Mark> marks = new ArrayList<>();
        for (Integer markIndex : metaMarks) {
            if (markIndex < 0 || markIndex >= metaValues.size()) {
                continue;
            }

            TraceGeoData metaValue = metaValues.get(markIndex);
            Mark mark = new Mark();
            mark.setTraceIndex(metaValue.getTraceIndex());
            marks.add(mark);
        }
        return marks;
    }

    public static @Nullable Meta toMeta(@Nullable MetaDocument metaDocument, ColumnSchema columnSchema) {
        return metaDocument != null ? metaDocument.toMeta(columnSchema) : null;
    }

    public Meta toMeta(ColumnSchema columnSchema) {
        Check.notNull(columnSchema);

        Meta meta = new Meta(columnSchema);
        meta.setSampleRange(sampleRange);
        meta.setContrast(contrast);
        meta.setBackgroundRemoved(backgroundRemoved);
        meta.setDepthRange(getDepthRange());

        List<TraceGeoData> metaValues = toMetaValues(columnSchema);
        meta.setValues(metaValues);
        Set<Integer> metaMarks = toMetaMarks(metaValues);
        meta.setMarks(metaMarks);

        return meta;
    }

    private List<TraceGeoData> toMetaValues(ColumnSchema columnSchema) {
        Check.notNull(columnSchema);

        List<Line> orderedLines = new ArrayList<>(Nulls.toEmpty(lines));
        if (orderedLines.size() > 1) {
            orderedLines.sort(Comparator.comparing(Line::getFrom));
        }

        int numValues = 0;
        for (Line line : orderedLines) {
            numValues += line.getTo() - line.getFrom();
        }

        int lineIndex = 0;
        List<TraceGeoData> metaValues = new ArrayList<>(numValues);
        for (Line line : orderedLines) {
            for (int i = line.getFrom(); i < line.getTo(); i++) {
                TraceGeoData metaValue = new TraceGeoData(columnSchema, i);
                metaValue.setLine(lineIndex);
                metaValues.add(metaValue);
            }
            lineIndex++;
        }
        return metaValues;
    }

    private Set<Integer> toMetaMarks(List<TraceGeoData> metaValues) {
        if (Nulls.isNullOrEmpty(metaValues)) {
            return Set.of();
        }

        Set<Integer> markIndices = new HashSet<>();
        for (Mark mark : Nulls.toEmpty(marks)) {
            markIndices.add(mark.getTraceIndex());
        }

        Set<Integer> metaMarks = new HashSet<>();
        for (int i = 0; i < metaValues.size(); i++) {
            TraceGeoData metaValue = metaValues.get(i);
            int traceIndex = metaValue.getTraceIndex();
            if (markIndices.contains(traceIndex)) {
                metaMarks.add(i);
            }
        }
        return metaMarks;
    }

    public static class Line {

        @Expose
        private int lineIndex;

        @Expose
        private int from;

        @Expose
        private int to;

        public int getLineIndex() {
            return lineIndex;
        }

        public void setLineIndex(int lineIndex) {
            this.lineIndex = lineIndex;
        }

        public int getFrom() {
            return from;
        }

        public void setFrom(int from) {
            this.from = from;
        }

        public int getTo() {
            return to;
        }

        public void setTo(int to) {
            this.to = to;
        }
    }

    public static class Mark {

        @Expose
        private int traceIndex;

        public int getTraceIndex() {
            return traceIndex;
        }

        public void setTraceIndex(int traceIndex) {
            this.traceIndex = traceIndex;
        }
    }
}
