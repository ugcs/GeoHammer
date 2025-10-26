package com.github.thecoldwine.sigrun.common.ext;

import com.ugcs.gprvisualizer.app.meta.SampleRange;
import com.ugcs.gprvisualizer.app.meta.TraceLine;
import com.ugcs.gprvisualizer.app.meta.TraceMark;
import com.ugcs.gprvisualizer.app.meta.TraceMeta;
import com.ugcs.gprvisualizer.app.parsers.GeoData;
import com.ugcs.gprvisualizer.app.parsers.Semantic;
import com.ugcs.gprvisualizer.app.quality.LineSchema;
import com.ugcs.gprvisualizer.utils.Check;
import com.ugcs.gprvisualizer.utils.FileNames;
import com.ugcs.gprvisualizer.utils.GsonConfig;
import com.ugcs.gprvisualizer.utils.Nulls;
import com.ugcs.gprvisualizer.utils.Range;
import com.ugcs.gprvisualizer.utils.Strings;
import com.ugcs.gprvisualizer.utils.Traces;

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
import java.util.TreeMap;

import javax.annotation.Nullable;

public class MetaFile {

    private static final String META_FILE_EXTENSION = ".geohammer";

    private SampleRange sampleRange;

	private @Nullable Double contrast;

	private @Nullable Range amplitudeRange;

    // mark position indices in a local values list
    private Set<Integer> marks = new HashSet<>();

    private List<TraceGeoData> values = new ArrayList<>();

    public SampleRange getSampleRange() {
        return sampleRange;
    }

    public void setSampleRange(SampleRange sampleRange) {
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
        SampleRange maxSampleRange = Traces.maxSampleRange(traces);
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
        String lineHeader = GeoData.getHeader(Semantic.LINE, null);
        TreeMap<Integer, Range> lineRanges = LineSchema.getLineRanges(values, lineHeader);
        List<TraceLine> lines = new ArrayList<>();
        for (Map.Entry<Integer, Range> e : lineRanges.entrySet()) {
            Integer lineIndex = e.getKey();
            Range lineRange = e.getValue();

            TraceLine line = new TraceLine();
            line.setLineIndex(lineIndex);

            TraceGeoData lineStart = values.get(lineRange.getMin().intValue());
            TraceGeoData lineEnd = values.get(lineRange.getMax().intValue());

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

        String lineHeader = GeoData.getHeader(Semantic.LINE, null);
        int lineIndex = 0;
        List<TraceGeoData> values = new ArrayList<>(numValues);
        for (TraceLine line : lines) {
            for (int i = line.getFrom(); i < line.getTo(); i++) {
                TraceGeoData value = new TraceGeoData(i);
                value.setSensorValue(lineHeader, lineIndex);
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
