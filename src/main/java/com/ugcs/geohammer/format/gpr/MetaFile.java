package com.ugcs.geohammer.format.gpr;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.gpr.meta.TraceLine;
import com.ugcs.geohammer.format.gpr.meta.TraceMark;
import com.ugcs.geohammer.format.gpr.meta.TraceMeta;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.model.LineSchema;
import com.ugcs.geohammer.format.TraceGeoData;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.FileNames;
import com.ugcs.geohammer.util.GsonConfig;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.model.Range;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.util.Traces;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

public class MetaFile {

    private static final String META_FILE_EXTENSION = ".geohammer";

    private @Nullable IndexRange sampleRange;

	private @Nullable Double contrast;

	private @Nullable Range amplitudeRange;

    // mark position indices in a local values list
    private Set<Integer> marks = new HashSet<>();

    private List<TraceGeoData> values = new ArrayList<>();

    public @Nullable IndexRange getSampleRange() {
        return sampleRange;
    }

    public void setSampleRange(@Nullable IndexRange sampleRange) {
        this.sampleRange = sampleRange;
    }

    public Set<Integer> getMarks() {
        return marks;
    }

    public void setMarks(Set<Integer> marks) {
        this.marks = marks;
    }

	public @Nullable Double getContrast() { return contrast; }

	public void setContrast(Double contrast) { this.contrast = contrast; }

	public @Nullable Range getAmplitudeRange() {
		return amplitudeRange;
	}

	public void setAmplitudeRange(Range amplitudeRange) {
		this.amplitudeRange = amplitudeRange;
	}

	public List<? extends GeoData> getValues() {
        return values;
    }

    public int numTraces() {
        return values.size();
    }

    // global trace index for ith value
    public int getTraceIndex(int index) {
        TraceGeoData value = values.get(index);
        return value.getTraceIndex();
    }

    public static Path getMetaPath(File source) {
        Check.notNull(source);

        String sourceBase = FileNames.removeExtension(source.getName());
        String metaFileName = Strings.nullToEmpty(sourceBase) + META_FILE_EXTENSION;

        return new File(source.getParentFile(), metaFileName).toPath();
    }

    public void initTraces(List<Trace> traces) {
        initLocations(traces);
        initSampleRanges(traces);
    }

    private void initLocations(List<Trace> traces) {
        if (traces == null) {
            return;
        }

        for (TraceGeoData value : values) {
            int traceIndex = value.getTraceIndex();
            Trace trace = traces.get(traceIndex);

            LatLon latLon = trace.getLatLon();
            if (latLon != null) {
                value.setLatLon(trace.getLatLon());
            }
        }
    }

    private void initSampleRanges(List<Trace> traces) {
        for (Trace trace : Nulls.toEmpty(traces)) {
            trace.setSampleRange(sampleRange);
        }
    }

    public void init(List<Trace> traces) {
        TraceMeta meta = getMetaFromTraces(traces);
        setMetaToState(meta);
    }

    public void load(Path path) throws IOException {
        Check.notNull(path);

        TraceMeta meta = readMeta(path);
        setMetaToState(meta);
    }

    public void save(Path path) throws IOException {
        Check.notNull(path);

        TraceMeta meta = getMetaFromState();
        writeMeta(meta, path);
    }

    private TraceMeta getMetaFromTraces(List<Trace> traces) {
        traces = Nulls.toEmpty(traces);

        TraceMeta meta = new TraceMeta();

        // sample range
        IndexRange maxSampleRange = Traces.maxSampleRange(traces);
        meta.setSampleRange(maxSampleRange);

        // lines
        TraceLine line = new TraceLine();
        line.setLineIndex(0);
        line.setFrom(0);
        line.setTo(traces.size());
        meta.setLines(List.of(line));

        // marks
        List<TraceMark> traceMarks = new ArrayList<>();
        for (int i = 0; i < traces.size(); i++) {
            Trace trace = traces.get(i);
            if (trace.isMarked()) {
                TraceMark traceMark = new TraceMark();
                traceMark.setTraceIndex(i);
                traceMarks.add(traceMark);
            }
        }
        meta.setMarks(traceMarks);

        return meta;
    }

    public TraceMeta getMetaFromState() {
        TraceMeta meta = new TraceMeta();

        // sample range
        meta.setSampleRange(sampleRange);

        // lines
        var lineRanges = LineSchema.getLineRanges(values);
        List<TraceLine> lines = new ArrayList<>();
        for (Map.Entry<Integer, IndexRange> e : lineRanges.entrySet()) {
            Integer lineIndex = e.getKey();
            IndexRange lineRange = e.getValue();

            TraceLine line = new TraceLine();
            line.setLineIndex(lineIndex);

            TraceGeoData lineStart = values.get(lineRange.from());
            TraceGeoData lineEnd = values.get(lineRange.to() - 1);

            line.setFrom(lineStart.getTraceIndex());
            line.setTo(lineEnd.getTraceIndex() + 1); // exclusive

            lines.add(line);
        }
        meta.setLines(lines);

        // marks
        List<TraceMark> traceMarks = new ArrayList<>();
        for (Integer markIndex : Nulls.toEmpty(marks)) {
            if (markIndex < 0 || markIndex >= values.size()) {
                continue;
            }

            TraceGeoData value = values.get(markIndex);
            TraceMark traceMark = new TraceMark();
            traceMark.setTraceIndex(value.getTraceIndex());

            traceMarks.add(traceMark);
        }
        meta.setMarks(traceMarks);

		meta.setContrast(contrast);
		meta.setAmplitudeRange(amplitudeRange);

        return meta;
    }

    public void setMetaToState(TraceMeta meta) {
        Check.notNull(meta);

        // sample range
        this.sampleRange = meta.getSampleRange();

        // lines
        List<TraceLine> lines = Nulls.toEmpty(meta.getLines());
        if (lines.size() > 1) {
            lines.sort(Comparator.comparing(TraceLine::getFrom));
        }
        int numValues = 0;
        for (TraceLine line : lines) {
            numValues += line.getTo() - line.getFrom();
        }

        int lineIndex = 0;
        List<TraceGeoData> values = new ArrayList<>(numValues);
        for (TraceLine line : lines) {
            for (int i = line.getFrom(); i < line.getTo(); i++) {
                TraceGeoData value = new TraceGeoData(i);
                value.setLine(lineIndex);
                values.add(value);
            }
            lineIndex++;
        }
        this.values = values;

        // marks
        Set<Integer> traceMarks = new HashSet<>();
        for (TraceMark traceMark : Nulls.toEmpty(meta.getMarks())) {
            traceMarks.add(traceMark.getTraceIndex());
        }

        Set<Integer> marks = new HashSet<>();
        for (int i = 0; i < values.size(); i++) {
            TraceGeoData value = values.get(i);
            int traceIndex = value.getTraceIndex();
            if (traceMarks.contains(traceIndex)) {
                marks.add(i);
            }
        }
        this.marks = marks;
		this.contrast = meta.getContrast();
		this.amplitudeRange = meta.getAmplitudeRange();
    }

    private TraceMeta readMeta(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            return GsonConfig.GSON.fromJson(reader, TraceMeta.class);
        }
    }

    private void writeMeta(TraceMeta traceMeta, Path path) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path)) {
            GsonConfig.GSON.toJson(traceMeta, TraceMeta.class, writer);
        }
    }
}
