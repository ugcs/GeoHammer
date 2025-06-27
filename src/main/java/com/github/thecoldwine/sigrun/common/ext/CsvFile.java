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
import java.util.stream.Collectors;

import com.ugcs.gprvisualizer.app.AppContext;
import com.ugcs.gprvisualizer.app.auxcontrol.FoundPlace;
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
        this.setParser(file.getParser());
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

            var foundPalces = getAuxElements().stream().filter(bo -> bo instanceof FoundPlace).map(bo -> ((FoundPlace) bo).getTraceIndex()).collect(Collectors.toSet());
            for (int i = 0; i < geoData.size(); i++) {
                GeoData gd = geoData.get(i);
                gd.setSensorValue(GeoData.Semantic.MARK.getName(), gd.isMarked() ? 1 : 0);
                gd.setSensorValue(GeoData.Semantic.MARK.getName(), foundPalces.contains(i) ? 1 : 0);
            }

            Map<Integer, GeoData> geoDataMap = getGeoData().stream().collect(Collectors.toMap(GeoData::getLineNumber, gd -> gd));

            Map<String, SensorData> semanticToSensorData = getParser().getTemplate().getDataMapping().getDataValues().stream()
                .collect(Collectors.toMap(dv -> dv.getSemantic(), dv -> dv));

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

                for(var semantic: semanticToSensorData.keySet()
                        .stream()
                        .filter(s -> s.contains("_anomaly")).toList()) {
                    if (semanticToSensorData.get(semantic) instanceof SensorData sd
                            && !skippedLines.contains(sd.getHeader())) {
                        // add "*_anomaly" to the end of the header if not exists
                        skippedLines = skippedLines.replaceAll(System.lineSeparator() + "$", "," + semanticToSensorData.get(semantic).getHeader() + System.lineSeparator());
                        getParser().setIndexByHeaderForSensorData(skippedLines, semanticToSensorData.get(semantic));
                    }
                };

                if (!skippedLines.contains(nextWPColumn.getHeader())) {
                    // add "Next WP" to the end of the header if not exists
                    skippedLines = skippedLines.replaceAll(System.lineSeparator() + "$", "," + nextWPColumn.getHeader() + System.lineSeparator());
                    getParser().setIndexByHeaderForSensorData(skippedLines, nextWPColumn);
                }

                if (!skippedLines.contains(markColumn.getHeader())) {
                    // add "Mark" to the end of the header if not exists
                    skippedLines = skippedLines.replaceAll(System.lineSeparator() + "$", "," + markColumn.getHeader() + System.lineSeparator());
                }
                getParser().setIndexByHeaderForSensorData(skippedLines, markColumn);
                semanticToSensorData.put(markColumn.getHeader(), markColumn);

				writer.write(skippedLines);

            	String line;
            	int lineNumber = 0;

            	while ((line = reader.readLine()) != null) {
                	lineNumber++;
                	if (geoDataMap.get(lineNumber) instanceof GeoData gd) {
                        for (var sv: gd.getSensorValues()) {   
                            if (sv.originalData() != sv.data()) {
                                var template = semanticToSensorData.get(sv.semantic());
                                //if (template == null) {
                                //    template = markColumn;
                                //}
                                //boolean isLast = skippedLines.endsWith(template.getHeader() + System.lineSeparator());
                                //System.out.println(template.getIndex());
                                line = replaceCsvValue(line, template.getIndex(), sv.data() != null ? String.format("%s", sv.data()) : "");
                            }
                        }
                    	writer.write(line);
                    	writer.newLine();
                	}
            	}
        	}
    }

    private static String replaceCsvValue(String input, int position, String newValue) {
        String[] parts = input.split(",", -1); // -1 for save empty string 
        if (position >= 0 && position < parts.length) {
            parts[position] = newValue;
        } else if (position >= parts.length) {
            parts = Arrays.copyOf(parts, position + 1);
            parts[parts.length - 1] = newValue;
            parts = Arrays.stream(parts).map(p -> p == null ? "" : p).toList().toArray(parts);
        }
        return String.join(",", parts);
    }

    @Override
    public List<GeoData> getGeoData() {
        return geoData;
    }

    public void setGeoData(List<GeoData> geoData) {
        this.geoData = geoData;
    }

    private void setParser(CsvParser parser) {
		this.parser = parser;
    }

    @Nullable
	public CsvParser getParser() {
		return parser;
	}

    @Override
    public CsvFile copy() {
        return new CsvFile(this);
    }

    public boolean isSameTemplate(CsvFile file) {
        return file.getParser().getTemplate().equals(getParser().getTemplate());
    }
}
