package com.github.thecoldwine.sigrun.common.ext;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.ugcs.gprvisualizer.app.AppContext;
import com.ugcs.gprvisualizer.app.auxcontrol.FoundPlace;
import com.ugcs.gprvisualizer.app.parcers.SensorValue;
import com.ugcs.gprvisualizer.app.undo.FileSnapshot;
import com.ugcs.gprvisualizer.app.yaml.Template;
import com.ugcs.gprvisualizer.gpr.Model;
import com.ugcs.gprvisualizer.utils.Nulls;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ugcs.gprvisualizer.app.parcers.GeoCoordinates;
import com.ugcs.gprvisualizer.app.parcers.GeoData;
import com.ugcs.gprvisualizer.app.parcers.csv.CSVParsersFactory;
import com.ugcs.gprvisualizer.app.parcers.csv.CsvParser;
import com.ugcs.gprvisualizer.app.yaml.FileTemplates;
import com.ugcs.gprvisualizer.app.yaml.data.SensorData;

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

        parser = new CSVParsersFactory().createCSVParser(fileTemplate);

        List<GeoCoordinates> coordinates = parser.parse(csvFileAbsolutePath);

        if (getFile() == null) {
            setFile(csvFile);
        }

        for (GeoCoordinates coord : coordinates) {
            // Added points if lat and lon are not 0 and point is close to the first point
            if (coord.getLatitude().intValue() != 0) {
                if (coord instanceof GeoData value) {
                    int traceIndex = geoData.size();
                    geoData.add(value);
                    if (value.isMarked()) {
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
        Integer prevLineIndex = null;
        int sequence = 0;
        for (int i = 0; i < geoData.size(); i++) {
            GeoData value = geoData.get(i);
            Integer lineIndex = value.getLineIndex().orElse(null);
            if (i > 0 && !Objects.equals(lineIndex, prevLineIndex)) {
                sequence++;
            }
            value.setLineIndex(sequence);
            prevLineIndex = lineIndex;
        }
    }

    @Override
    public void save(File file) throws IOException {
        Path path = file.toPath();

        var foundPlaces = getAuxElements().stream()
                .filter(bo -> bo instanceof FoundPlace)
                .map(bo -> ((FoundPlace) bo).getTraceIndex())
                .collect(Collectors.toSet());
        for (int i = 0; i < geoData.size(); i++) {
            GeoData gd = geoData.get(i);
            boolean marked = gd.isMarked() || foundPlaces.contains(i);
            gd.setSensorValue(GeoData.Semantic.MARK.getName(), marked ? 1 : 0);
        }

        String separator = parser.getTemplate().getFileFormat().getSeparator();

        Map<String, SensorData> semanticToSensorData = parser.getTemplate().getDataMapping().getDataValues().stream()
                .collect(Collectors.toMap(SensorData::getSemantic, dv -> dv));

        String skippedLines = parser.getSkippedLines();

        Set<String> presentedSemantics = geoData.stream()
                .filter(gd -> gd.getSensorValues() != null)
                .flatMap(value -> value.getSensorValues().stream()
                        .map(SensorValue::semantic)
                ).collect(Collectors.toSet());
        Set<String> semanticsToSave = getSemanticsToSave(presentedSemantics, semanticToSensorData, skippedLines);
        Set<String> newlyAddedSemantics = new LinkedHashSet<>();

        for (var semantic : semanticsToSave) {
            SensorData sensorData = semanticToSensorData.get(semantic);
            String header = sensorData != null ? sensorData.getHeader() : semantic;
            if (!skippedLines.contains(header)) {
                skippedLines = skippedLines.replaceAll(System.lineSeparator() + "$", separator + header + System.lineSeparator());
                if (sensorData == null) {
                    sensorData = semanticToSensorData.getOrDefault(semantic, new SensorData() {{
                        setHeader(semantic);
                    }});
                    semanticToSensorData.put(semantic, sensorData);
                }
                parser.setIndexByHeaderForSensorData(skippedLines, sensorData);
                newlyAddedSemantics.add(semantic);
            }
        }

        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.write(skippedLines);

            String line;
            for (GeoData gd : geoData) {
                if (gd == null) {
                    continue;
                }
                line = gd.getSourceLine();
                for (var sv : gd.getSensorValues()) {
                    if (!semanticsToSave.contains(sv.semantic())) {
                        continue;
                    }

                    if (newlyAddedSemantics.contains(sv.semantic()) || !Objects.equals(sv.originalData(), sv.data())) {
                        var template = semanticToSensorData.get(sv.semantic());
                        line = replaceCsvValue(line, separator, Objects.requireNonNull(template).getIndex(), sv.data() != null ? String.format("%s", sv.data()) : "");
                        gd.setSourceLine(line);
                    }
                }
                writer.write(line);
                writer.newLine();
            }
        }
    }

    private Set<String> getSemanticsToSave(Set<String> presentedSemantics, Map<String, SensorData> semanticToSensorData, String skippedLines) {
        Set<String> semanticsToSave = new LinkedHashSet<>();
        for (String semantic : presentedSemantics) {
			SensorData sensorData = semanticToSensorData.get(semantic);
			String header = sensorData != null ? sensorData.getHeader() : semantic;
            if (skippedLines.contains(header)) {
                semanticsToSave.add(semantic);
            } else if (hasAnyValidValue(geoData, semantic)) {
                semanticsToSave.add(semantic);
            }
        }
        return semanticsToSave;
    }

    private static String replaceCsvValue(String input, String separator, int position, String newValue) {
        String[] parts = input.split(separator, -1); // -1 for save empty string
        if (position >= 0 && position < parts.length) {
            parts[position] = newValue;
        } else if (position >= parts.length) {
            parts = Arrays.copyOf(parts, position + 1);
            parts[parts.length - 1] = newValue;
            parts = Arrays.stream(parts).map(part -> part == null ? "" : part).toList().toArray(parts);
        }
        return String.join(separator, parts);
    }

    private boolean hasAnyValidValue(List<GeoData> geoData, String semantic) {
        for (GeoData gd : geoData) {
            SensorValue sv = gd.getSensorValue(semantic);
            if (sv != null && sv.data() != null) {
                return true;
            }
        }
        return false;
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
