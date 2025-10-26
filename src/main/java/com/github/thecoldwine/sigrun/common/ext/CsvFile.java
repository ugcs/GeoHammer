package com.github.thecoldwine.sigrun.common.ext;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.ugcs.gprvisualizer.app.AppContext;
import com.ugcs.gprvisualizer.app.auxcontrol.FoundPlace;
import com.ugcs.gprvisualizer.app.parsers.Semantic;
import com.ugcs.gprvisualizer.app.parsers.SensorValue;
import com.ugcs.gprvisualizer.app.undo.FileSnapshot;
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

        String csvFileAbsolutePath = csvFile.getAbsolutePath();
        var fileTemplate = fileTemplates.findTemplate(fileTemplates.getTemplates(), csvFileAbsolutePath);

        if (fileTemplate == null) {
            throw new RuntimeException("Can`t find template for file " + csvFile.getName());
        }

        log.debug("template: {}", fileTemplate.getName());

        parser = new CsvParserFactory().createCsvParser(fileTemplate);

        List<GeoCoordinates> coordinates = parser.parse(csvFileAbsolutePath);

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

        var foundPlaces = getAuxElements().stream()
                .filter(bo -> bo instanceof FoundPlace)
                .map(bo -> ((FoundPlace) bo).getTraceIndex())
                .collect(Collectors.toSet());
        String markHeader = GeoData.getHeader(Semantic.MARK, getTemplate());
        for (int i = 0; i < geoData.size(); i++) {
            GeoData value = geoData.get(i);
            boolean marked = foundPlaces.contains(i);
            value.setSensorValue(markHeader, marked ? 1 : 0);
        }

        String separator = getTemplate().getFileFormat().getSeparator();
        String skippedLines = parser.getSkippedLines();

        Map<String, Integer> headers = parser.getHeaders();
        Set<String> dataHeaders = getDataHeaders();

        // update headers
        for (String header : dataHeaders) {
            if (!skippedLines.contains(header)) {
                skippedLines = skippedLines.replaceAll(
                        System.lineSeparator() + "$",
                        separator + header + System.lineSeparator());
                parser.getHeaders().put(header, headers.size());
            }
        }

        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath())) {
            writer.write(skippedLines);

            for (GeoData gd : geoData) {
                if (gd == null) {
                    continue;
                }
                // copy source line
                String[] sourceLine = gd.getSourceLine();
                String[] line = Arrays.copyOf(sourceLine, headers.size());
                for (int i = sourceLine.length; i < line.length; i++) {
                    line[i] = Strings.empty();
                }

                for (var sensorValue : gd.getSensorValues()) {
                    String header = sensorValue.header();
                    Integer columnIndex = headers.get(header);
                    if (columnIndex == null || columnIndex < 0 || columnIndex >= line.length) {
                        continue;
                    }

                    String sourceValue = columnIndex < sourceLine.length
                            ? sourceLine[columnIndex]
                            : null;
                    Number sourceNumber = parser.parseNumber(null, sourceValue);
                    if (columnIndex >= sourceLine.length || !Objects.equals(sensorValue.data(), sourceNumber)) {
                        String value = sensorValue.data() != null
                                ? String.format("%s", sensorValue.data())
                                : Strings.empty();

                        line[columnIndex] = value;
                    }
                }
                gd.setSourceLine(line);
                writer.write(String.join(separator, line));
                writer.newLine();
            }
        }
    }

    private Set<String> getDataHeaders() {
        Map<String, Integer> headers = parser.getHeaders();
        Set<String> dataHeaders = new HashSet<>();

        for (GeoData value : Nulls.toEmpty(geoData)) {
            if (value == null) {
                continue;
            }
            for (SensorValue sensorValue : Nulls.toEmpty(value.getSensorValues())) {
                if (sensorValue == null) {
                    continue;
                }
                String header = sensorValue.header();
                if (Strings.isNullOrEmpty(header)) {
                    continue;
                }
                if (headers.containsKey(header) || sensorValue.data() != null) {
                    dataHeaders.add(header);
                }
            }
        }
        return dataHeaders;
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
