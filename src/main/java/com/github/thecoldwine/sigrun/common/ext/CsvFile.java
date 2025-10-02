package com.github.thecoldwine.sigrun.common.ext;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.ugcs.gprvisualizer.app.AppContext;
import com.ugcs.gprvisualizer.app.auxcontrol.FoundPlace;
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
import org.springframework.util.StringUtils;

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
            if (coord.getLatitude().intValue() != 0
                    && (geoData.isEmpty()
                        || (Math.abs(coord.getLatitude().intValue() 
                            - geoData.getFirst().getLatitude().intValue()) <= 1
                        && Math.abs(coord.getLongitude().intValue() 
                            - geoData.getFirst().getLongitude().intValue()) <= 1))) {
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
		Path inputFile = getFile().toPath();
		Path tempFile = file.toPath();

		var foundPlaces = getAuxElements().stream()
				.filter(bo -> bo instanceof FoundPlace)
				.map(bo -> ((FoundPlace) bo).getTraceIndex())
				.collect(Collectors.toSet());
		for (int i = 0; i < geoData.size(); i++) {
			GeoData currentGeoData = this.geoData.get(i);
			boolean needMark = currentGeoData.isMarked() || foundPlaces.contains(i);
			currentGeoData.setSensorValue(GeoData.Semantic.MARK.getName(), needMark ? 1 : 0);
		}

		Map<Integer, GeoData> geoDataMap = getGeoData().stream()
				.collect(Collectors.toMap(GeoData::getLineNumber, geoData -> geoData));

		String separator = parser.getTemplate().getFileFormat().getSeparator();
		Map<String, SensorData> semanticToSensorData = parser.getTemplate().getDataMapping().getDataValues().stream()
				.collect(Collectors.toMap(SensorData::getSemantic, Function.identity()));

		try (BufferedReader reader = Files.newBufferedReader(inputFile);
			 BufferedWriter writer = Files.newBufferedWriter(tempFile)) {

			String skippedLines = parser.getSkippedLines();

			// check if "Next WP" exists and is it the last column
			SensorData nextWPColumn = semanticToSensorData.getOrDefault(GeoData.Semantic.LINE.getName(), new SensorData() {{
				setHeader("Next WP");
			}});
			SensorData markColumn = semanticToSensorData.getOrDefault(GeoData.Semantic.MARK.getName(), new SensorData() {{
				setHeader("Mark");
			}});
			log.debug("Source file skippedLines: {}", skippedLines);

			for (var semantic : semanticToSensorData.keySet()
					.stream()
					.filter(semantic -> semantic.contains(GeoData.ANOMALY_SEMANTIC_SUFFIX))
					.toList()) {
				if (semanticToSensorData.get(semantic) instanceof SensorData sensorData
						&& !skippedLines.contains(sensorData.getHeader())) {
					// add "*_anomaly" to the end of the header if not exists
					skippedLines = skippedLines.replaceAll(
							System.lineSeparator() + "$",
							separator + semanticToSensorData.get(semantic).getHeader() + System.lineSeparator());
					parser.setIndexByHeaderForSensorData(skippedLines, semanticToSensorData.get(semantic));
				}
			}

			if (!skippedLines.contains(nextWPColumn.getHeader())) {
				// add "Next WP" to the end of the header if not exists
				skippedLines = skippedLines.replaceAll(
						System.lineSeparator() + "$",
						separator + nextWPColumn.getHeader() + System.lineSeparator());
				parser.setIndexByHeaderForSensorData(skippedLines, nextWPColumn);
			}

			if (!skippedLines.contains(markColumn.getHeader())) {
				// add "Mark" to the end of the header if not exists
				skippedLines = skippedLines.replaceAll(
						System.lineSeparator() + "$",
						separator + markColumn.getHeader() + System.lineSeparator());
			}
			parser.setIndexByHeaderForSensorData(skippedLines, markColumn);
			semanticToSensorData.put(markColumn.getHeader(), markColumn);

			String[] headers = skippedLines.split(System.lineSeparator(), -1);
			int headersSize = headers.length - 1;
			while (headersSize > 0 && headers[headersSize].isEmpty()) {
				headersSize--;
			}
			String headerLine = headers[headersSize];

			List<String> anomalySemantics = semanticToSensorData.keySet().stream()
					.filter(semantic -> semantic.endsWith(GeoData.ANOMALY_SEMANTIC_SUFFIX))
					.toList();
			for (String semantic : anomalySemantics) {
				parser.setIndexByHeaderForSensorData(skippedLines, semanticToSensorData.get(semantic));
			}

			boolean hasAnyMark = geoData.stream().anyMatch(geoData ->
					geoData.getSensorValues().stream().anyMatch(sensorValue ->
							GeoData.Semantic.MARK.getName().equals(sensorValue.semantic())
									&& Objects.equals(sensorValue.data(), 1))
			);

			List<Integer> indicesToRemove = new ArrayList<>();
			if (markColumn.getIndex() >= 0 && !hasAnyMark) {
				indicesToRemove.add(markColumn.getIndex());
			}

			Map<String, Boolean> anomalyHasData = anomalySemantics.stream().collect(Collectors.toMap(
					semantic -> semantic,
					semantic -> geoData.stream().anyMatch(geoData -> columnHasData(geoData, semantic))
			));

			for (String semantic : anomalySemantics) {
				SensorData sensorData = semanticToSensorData.get(semantic);
				boolean hasData = anomalyHasData.getOrDefault(semantic, false);
				if (sensorData != null && sensorData.getIndex() >= 0 && !hasData) {
					indicesToRemove.add(sensorData.getIndex());
				}
			}

			// Descending order removal to keep indices valid
			List<Integer> headerRemoval = indicesToRemove.stream()
					.sorted((a, b) -> Integer.compare(b, a))
					.toList();
			for (int index : headerRemoval) {
				headerLine = removeColumnData(headerLine, separator, index);
			}
			headers[headersSize] = headerLine;
			skippedLines = String.join(System.lineSeparator(), headers);
			if (!skippedLines.endsWith(System.lineSeparator())) {
				skippedLines = skippedLines + System.lineSeparator();
			}

			for (SensorData sensorData : semanticToSensorData.values()) {
				parser.setIndexByHeaderForSensorData(skippedLines, sensorData);
			}
			parser.setIndexByHeaderForSensorData(skippedLines, markColumn);
			parser.setIndexByHeaderForSensorData(skippedLines, nextWPColumn);

			writer.write(skippedLines);

			// Prepare descending order list of indices to remove
			List<Integer> rowRemovalIndices = indicesToRemove.stream()
					.sorted((a, b) -> Integer.compare(b, a))
					.toList();

			String line;
			int lineNumber = 0;

			while ((line = reader.readLine()) != null) {
				lineNumber++;
				if (geoDataMap.get(lineNumber) instanceof GeoData gd) {
					for (var sensorValue : gd.getSensorValues()) {
						var template = semanticToSensorData.get(sensorValue.semantic());
						if (template != null && sensorValue.originalData() != sensorValue.data()) {
							line = replaceCsvValue(
									line, separator, template.getIndex(),
									sensorValue.data() != null ? String.format("%s", sensorValue.data()) : ""
							);
						}
					}

					for (int index : rowRemovalIndices) {
						line = removeColumnData(line, separator, index);
					}
					writer.write(line);
					writer.newLine();
				}
			}
		}
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

	private static String removeColumnData(String row, String separator, int columnIndex) {
		String[] parts = row.split(separator, -1);
		if (columnIndex < 0 || columnIndex >= parts.length) {
			return row;
		}
		StringBuilder stringBuilder = new StringBuilder();
		for (int i = 0; i < parts.length; i++) {
			if (i == columnIndex) {
				continue;
			}
			if (!stringBuilder.isEmpty()) {
				stringBuilder.append(separator);
			}
			stringBuilder.append(parts[i]);
		}
		return stringBuilder.toString();
	}

	private boolean columnHasData(GeoData geoData, String headerName) {
		if (geoData == null || geoData.getSensorValues() == null) {
			return false;
		}
		return geoData.getSensorValues().stream()
				.anyMatch(sensorValue -> sensorValue.semantic().equals(headerName)
						&& sensorValue.data() != null
						&& StringUtils.hasText(sensorValue.data().toString()));
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
