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
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.ugcs.gprvisualizer.app.AppContext;
import com.ugcs.gprvisualizer.app.auxcontrol.FoundPlace;
import com.ugcs.gprvisualizer.utils.Range;
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
    public void open(File csvFile) throws Exception {

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
                    && (getGeoData().isEmpty()  
                        || (Math.abs(coord.getLatitude().intValue() 
                            - getGeoData().get(0).getLatitude().intValue()) <= 1  
                        && Math.abs(coord.getLongitude().intValue() 
                            - getGeoData().get(0).getLongitude().intValue()) <= 1))) {
                Trace tr = new Trace(this, null, null, new float[] {},
                        new LatLon(coord.getLatitude(), coord.getLongitude()));
                getTraces().add(tr);
                if (coord instanceof GeoData) {
                    getGeoData().add((GeoData) coord);
                }
                if (((GeoData) coord).isMarked()) {
                    getAuxElements().add(new FoundPlace(tr, getOffset(), AppContext.model));
                }
            }            
        }

        setUnsaved(false);
    }

    @Override
    public void save(File file) throws Exception {
			Path inputFile = getFile().toPath();
        	Path tempFile = file.toPath();

            var foundPalces = getAuxElements().stream().filter(bo -> bo instanceof FoundPlace).map(bo -> ((FoundPlace) bo).getTraceInFile()).collect(Collectors.toSet());
            getGeoData().forEach(gd -> {
                gd.setSensorValue(GeoData.Semantic.MARK.getName(), gd.isMarked() ? 1 : 0);
                gd.setSensorValue(GeoData.Semantic.MARK.getName(), foundPalces.contains(gd.getTraceNumber()) ? 1 : 0);
            });

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
                    if (!skippedLines.contains(semanticToSensorData.get(semantic).getHeader())) {
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
                	if (geoDataMap.keySet().contains(lineNumber)) {
						GeoData gd = geoDataMap.get(lineNumber);

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

        	} catch (IOException e) {
            	e.printStackTrace();
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

    public List<GeoData> getGeoData() {
        return geoData;
    }

    public void setGeoData(List<GeoData> geoData) {
        this.geoData = geoData;
    }

    private void setParser(CsvParser parser) {
		this.parser = parser;
    }

	public CsvParser getParser() {
		return parser;
	}

    @Override
    public CsvFile copy() {
        return new CsvFile(this);
    }

    @Override
    public void saveAux(File file) throws Exception {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'saveAux'");
    }

    @Override
    public double getSamplesToCmGrn() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getSamplesToCmGrn'");
    }

    @Override
    public double getSamplesToCmAir() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getSamplesToCmAir'");
    }

    @Override
    public SgyFile copyHeader() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'copyHeader'");
    }

    @Override
    public int getSampleInterval() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getSampleInterval'");
    }

    public boolean isSameTemplate(CsvFile file) {
        return file.getParser().getTemplate().equals(getParser().getTemplate());
    }

    @Override
    public void updateTraces() {
        super.updateTraces();
        for (int i = 0; i < Math.min(getGeoData().size(), getTraces().size()); i++) {
            getGeoData().get(i).setTraceNumber(i);
        }
    }

    public SortedMap<Integer, Range> getLineRanges() {
        List<GeoData> values = getGeoData();
        // line index -> [first index, last index]
        TreeMap<Integer, Range> ranges = new TreeMap<>();
        if (values == null)
            return ranges;

        int lineIndex = 0;
        int lineStart = 0;
        for (int i = 0; i < values.size(); i++) {
            GeoData value = values.get(i);
            if (value == null)
                continue;

            int valueLineIndex = value.getLineIndex();
            if (valueLineIndex != lineIndex) {
                if (i > lineStart) {
                    ranges.put(lineIndex, new Range(lineStart, i - 1));
                }
                lineIndex = valueLineIndex;
                lineStart = i;
            }
        }
        if (values.size() > lineStart) {
            ranges.put(lineIndex, new Range(lineStart, values.size() - 1));
        }
        return ranges;
    }

}
