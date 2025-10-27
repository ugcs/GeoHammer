package com.github.thecoldwine.sigrun.common.ext;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.ugcs.gprvisualizer.app.AppContext;
import com.ugcs.gprvisualizer.app.auxcontrol.FoundPlace;
import com.ugcs.gprvisualizer.app.parsers.Semantic;
import com.ugcs.gprvisualizer.app.parsers.SensorValue;
import com.ugcs.gprvisualizer.app.undo.FileSnapshot;
import com.ugcs.gprvisualizer.app.yaml.FileFormat;
import com.ugcs.gprvisualizer.app.yaml.Template;
import com.ugcs.gprvisualizer.gpr.Model;
import com.ugcs.gprvisualizer.utils.Check;
import com.ugcs.gprvisualizer.utils.Nulls;
import com.ugcs.gprvisualizer.utils.Strings;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ugcs.gprvisualizer.app.parsers.GeoCoordinates;
import com.ugcs.gprvisualizer.app.parsers.GeoData;
import com.ugcs.gprvisualizer.app.parsers.csv.CsvParserFactory;
import com.ugcs.gprvisualizer.app.parsers.csv.CsvParser;
import com.ugcs.gprvisualizer.app.yaml.FileTemplates;

public class CsvFile extends SgyFile {

	private static final Logger log = LoggerFactory.getLogger(CsvFile.class.getName());

	private List<GeoData> geoData = new ArrayList<>();

	@Nullable
	private CsvParser parser;

	private FileTemplates fileTemplates;

	public CsvFile(FileTemplates fileTemplates) {
		this.fileTemplates = fileTemplates;
	}

    private CsvFile(CsvFile file) {
        this(file.fileTemplates);
        this.setFile(file.getFile());
        this.parser = file.parser;
    }

    @Override
    public int numTraces() {
        return geoData.size();
    }

    @Override
    public void open(File csvFile) throws IOException {
        String path = csvFile.getAbsolutePath();
        Template template = fileTemplates.findTemplate(fileTemplates.getTemplates(), path);
        if (template == null) {
            throw new RuntimeException("Can`t find template for file " + csvFile.getName());
        }

        log.debug("template: {}", template.getName());

        parser = new CsvParserFactory().createCsvParser(template);
        List<GeoCoordinates> coordinates = parser.parse(path);

        if (getFile() == null) {
            setFile(csvFile);
        }

        String markHeader = GeoData.getHeader(Semantic.MARK, getTemplate());
        for (GeoCoordinates coord : coordinates) {
            // Added points if lat and lon are not 0 and point is close to the first point
            if (coord.getLatitude() != 0.0 && coord.getLongitude() != 0.0) {
                if (coord instanceof GeoData value) {
                    int traceIndex = geoData.size();
                    geoData.add(value);
                    // create mark
                    boolean marked = value.getInt(markHeader).orElse(0) == 1;
                    if (marked) {
                        TraceKey traceKey = new TraceKey(this, traceIndex);
                        getAuxElements().add(new FoundPlace(traceKey, AppContext.model));
                    }
                }
            }
        }

        reorderLines();

        setUnsaved(false);
    }

    private void reorderLines() {
        if (geoData == null) {
            return;
        }
        String lineHeader = GeoData.getHeader(Semantic.LINE, getTemplate());
        Integer prevLineIndex = null;
        int sequence = 0;
        for (int i = 0; i < geoData.size(); i++) {
            GeoData value = geoData.get(i);
            Integer lineIndex = value.getInt(lineHeader).orElse(null);
            if (i > 0 && !Objects.equals(lineIndex, prevLineIndex)) {
                sequence++;
            }
            value.setSensorValue(lineHeader, sequence);
            prevLineIndex = lineIndex;
        }
    }

    @Override
    public void save(File file) throws IOException {
        Check.notNull(file);

        var marks = getAuxElements().stream()
                .filter(bo -> bo instanceof FoundPlace)
                .map(bo -> ((FoundPlace) bo).getTraceIndex())
                .collect(Collectors.toSet());
        String markHeader = GeoData.getHeader(Semantic.MARK, getTemplate());
        for (int i = 0; i < geoData.size(); i++) {
            GeoData value = geoData.get(i);
            boolean marked = marks.contains(i);
            value.setSensorValue(markHeader, marked ? 1 : 0);
        }

        FileFormat fileFormat = getTemplate().getFileFormat();

        Map<String, Integer> newHeaders = getHeadersToSave();

        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath())) {
            // skipped lines
            for (String line : Nulls.toEmpty(parser.getSkippedLines())) {
                writer.write(line);
                writer.newLine();
            }

            // headers
            if (fileFormat.isHasHeader()) {
                writer.write(String.join(fileFormat.getSeparator(), orderHeaders(newHeaders)));
                writer.newLine();
            }

            // data lines
            for (GeoData value : geoData) {
                if (value == null) {
                    continue;
                }
                // copy source line
                String[] line = Arrays.copyOf(value.getSourceLine(), newHeaders.size());
                for (var sensorValue : value.getSensorValues()) {
                    String header = sensorValue.header();
                    Integer columnIndex = newHeaders.get(header);
                    if (columnIndex == null || columnIndex < 0 || columnIndex >= line.length) {
                        continue;
                    }
                    line[columnIndex] = sensorValue.data() != null
                                ? String.format("%s", sensorValue.data())
                                : Strings.empty();
                }
                writer.write(String.join(fileFormat.getSeparator(), line));
                writer.newLine();

                value.setSourceLine(line);
            }

            parser.setHeaders(newHeaders);
        }
    }

    private Map<String, Integer> getHeadersToSave() {
        Map<String, Integer> newHeaders = new HashMap<>(parser.getHeaders());
        for (GeoData value : Nulls.toEmpty(geoData)) {
            if (value == null) {
                continue;
            }
            for (SensorValue sensorValue : Nulls.toEmpty(value.getSensorValues())) {
                if (sensorValue == null || sensorValue.data() == null) {
                    continue;
                }
                String header = sensorValue.header();
                if (Strings.isNullOrEmpty(header)) {
                    continue;
                }
                if (!newHeaders.containsKey(header)) {
                    newHeaders.put(header, newHeaders.size());
                }
            }
        }
        return newHeaders;
    }

    private List<String> orderHeaders(Map<String, Integer> headers) {
        headers = Nulls.toEmpty(headers);
        List<String> ordered = new ArrayList<>(Collections.nCopies(headers.size(), Strings.empty()));
        headers.forEach((header, index) -> ordered.set(index, header));
        return ordered;
    }

    @Override
    public List<GeoData> getGeoData() {
        return geoData;
    }

    public void setGeoData(List<GeoData> geoData) {
        this.geoData = geoData;
    }

    @Nullable
    public CsvParser getParser() {
        return parser;
    }

    @Nullable
    public Template getTemplate() {
        CsvParser parser = this.parser;
        return parser != null ? parser.getTemplate() : null;
    }

    @Override
    public CsvFile copy() {
        return new CsvFile(this);
    }

    @Override
    public FileSnapshot<CsvFile> createSnapshot() {
        return new Snapshot(this);
    }

	public void loadFrom(CsvFile other) {
        this.parser = other.getParser();
		this.setGeoData(other.getGeoData());
		this.setAuxElements(other.getAuxElements());
		this.setUnsaved(true);
	}

    public boolean isSameTemplate(CsvFile file) {
        return Objects.equals(file.getTemplate(), getTemplate());
    }

    public static class Snapshot extends FileSnapshot<CsvFile> {

        private final List<GeoData> values;

        public Snapshot(CsvFile file) {
            super(file);

            this.values = copyValues(file);
        }

        private static List<GeoData> copyValues(CsvFile file) {
            List<GeoData> values = Nulls.toEmpty(file.getGeoData());
            List<GeoData> snapshot = new ArrayList<>(values.size());
            for (GeoData value : values) {
                snapshot.add(new GeoData(value));
            }
            return snapshot;
        }

        @Override
        public void restoreFile(Model model) {
            file.setGeoData(values);
        }
    }
}
