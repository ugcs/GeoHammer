package com.ugcs.gprvisualizer.app.parsers.csv;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;

import com.ugcs.gprvisualizer.app.parsers.*;
import com.ugcs.gprvisualizer.app.parsers.exceptions.CsvParsingException;
import com.ugcs.gprvisualizer.app.parsers.exceptions.IncorrectDateFormatException;
import com.ugcs.gprvisualizer.app.yaml.DataMapping;
import com.ugcs.gprvisualizer.app.yaml.data.SensorData;
import com.ugcs.gprvisualizer.utils.Check;
import com.ugcs.gprvisualizer.utils.Nulls;
import com.ugcs.gprvisualizer.utils.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ugcs.gprvisualizer.app.parsers.exceptions.ColumnsMatchingException;
import com.ugcs.gprvisualizer.app.yaml.Template;
import com.ugcs.gprvisualizer.app.yaml.data.BaseData;
import com.ugcs.gprvisualizer.app.yaml.data.Date.Source;

public class CsvParser extends Parser {

    private static final Logger log = LoggerFactory.getLogger(CsvParser.class);

    public CsvParser(Template template) {
        super(template);
    }

    @Override
    public List<GeoCoordinates> parse(String path) throws FileNotFoundException {
        Check.notNull(template, "Template is not set");

        File file = new File(path);
        try {
            return parseFile(file);
        } catch (FileNotFoundException | CancellationException | IncorrectDateFormatException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            String message = Strings.nullToEmpty(e.getMessage())
                    + ", used template: " + template.getName();
            throw new CsvParsingException(file, message);
        }
    }

    private List<GeoCoordinates> parseFile(File file) throws IOException {
        Check.notNull(file);
        Check.notNull(template);

        DataMapping mapping = template.getDataMapping();

        // date from filename
        if (mapping.getDate() != null && mapping.getDate().getSource() == Source.FileName) {
            dateFromFilename = parseDateFromFilename(file.getName());
        }

        // header -> index
        this.headers = new HashMap<>();
        List<String[]> data = new ArrayList<>();

        try (var r = new BufferedReader(new FileReader(file))) {
            String line = skipLines(r);

            // reade header
            if (template.getFileFormat().isHasHeader()) {
                line = skipBlankAndComments(r, line);
                Check.notNull(line, "No header found");

                headers = parseHeaders(line);
                requireLocationHeaders();
            }

            // read data lines
            while ((line = r.readLine()) != null) {
                if (isBlankOrCommented(line)) {
                    continue;
                }

                String[] values = splitLine(line);
                data.add(values);
            }
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException();
        }

        List<GeoCoordinates> coordinates = parseData(headers, data);

        // timestamps could be in wrong order in the file
        if (hasHeader(mapping.getTimestamp())) {
            coordinates.sort(Comparator.comparing(GeoCoordinates::getDateTime));
        }

        return coordinates;
    }

    private String[] splitLine(String line) {
        if (Strings.isNullOrEmpty(line)) {
            return new String[0];
        }
        String[] tokens = line.split(template.getFileFormat().getSeparator());
        for (int i = 0; i < tokens.length; i++) {
            tokens[i] = tokens[i].trim();
        }
        return tokens;
    }

    // header -> index in a file headers line
    protected Map<String, Integer> parseHeaders(String line) {
        String[] tokens = splitLine(line);
        Map<String, Integer> headers = new HashMap<>(tokens.length);
        for (int i = 0; i < tokens.length; i++) {
            headers.put(tokens[i].trim(), i);
        }
        return headers;
    }

    private List<String> getDataHeaders(Map<String, Integer> headers, List<String[]> data) {
        Check.notNull(headers);

        List<String> dataHeaders = new ArrayList<>();

        // columns declared in template:
        // - present in a file
        // - is line or mark column
        // - is anomaly column
        for (SensorData column : Nulls.toEmpty(template.getDataMapping().getDataValues())) {
            if (column == null) {
                continue;
            }
            String header = column.getHeader();
            if (headers.containsKey(header)) {
                dataHeaders.add(header);
                continue;
            }
            String semantic = column.getSemantic();
            if (Objects.equals(semantic, Semantic.LINE.getName()) ||
                    Objects.equals(semantic, Semantic.MARK.getName())) {
                dataHeaders.add(header);
                continue;
            }
            if (semantic.endsWith(Semantic.ANOMALY_SUFFIX)) {
                String sourceSemantic = semantic.substring(
                        0, semantic.length() - Semantic.ANOMALY_SUFFIX.length());
                SensorData sourceColumn = template.getDataMapping().getDataValueBySemantic(sourceSemantic);
                if (sourceColumn != null && headers.containsKey(sourceColumn.getHeader())) {
                    dataHeaders.add(header);
                }
            }
        }

        // non-empty columns with numbers
        Set<String> templateHeaders = new HashSet<>(dataHeaders);
        List<String> nonTemplateHeaders = new ArrayList<>();
        for (Map.Entry<String, Integer> e : headers.entrySet()) {
            String header = e.getKey();
            if (templateHeaders.contains(header)) {
                continue;
            }
            Integer columnIndex = e.getValue();
            if (isNonEmptyNumericColumn(data, columnIndex)) {
                nonTemplateHeaders.add(header);
            }
        }
        nonTemplateHeaders.sort(Comparator.naturalOrder());
        dataHeaders.addAll(nonTemplateHeaders);

        return dataHeaders;
    }

    private boolean isNonEmptyNumericColumn(List<String[]> data, int columnIndex) {
        for (String[] values : Nulls.toEmpty(data)) {
            if (columnIndex < 0 || columnIndex >= values.length) {
                continue;
            }
            Number number = parseNumber(null, values[columnIndex]);
            if (number != null) {
                return true;
            }
        }
        return false;
    }

    private List<GeoCoordinates> parseData(Map<String, Integer> headers, List<String[]> data) {
        DataMapping mapping = template.getDataMapping();

        // list of data headers to load
        var dataHeaders = getDataHeaders(headers, data);

        List<GeoCoordinates> coordinates = new ArrayList<>();

        var traceCount = 0;
        for (String[] values : data) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            if (values == null || values.length == 0) {
                continue;
            }

            Double latitude = parseLatitude(values);
            Double longitude = parseLongitude(values);

            if (latitude == null || longitude == null) {
                continue;
            }

            Double altitude = parseAltitude(values);

            LocalDateTime dateTime = parseDateTime(values);

            Integer traceNumber = parseTraceNumber(values);
            if (traceNumber == null) {
                traceNumber = traceCount;
            }

            boolean marked = parseMark(values);

            List<SensorValue> sensorValues = new ArrayList<>();
            for (String header : dataHeaders) {
                // parse and add value
                Number number = parseNumber(values, header);
                // get column definition from the template
                SensorData column = mapping.getDataValueByHeader(header);
                SensorValue sensorValue = new SensorValue(
                        header,
                        column != null ? column.getUnits() : null,
                        number);
                sensorValues.add(sensorValue);
            }

            traceCount++;

            var geoCoordinates = new GeoCoordinates(dateTime, latitude, longitude, altitude, traceNumber);
            var geoData = new GeoData(marked, values, sensorValues, geoCoordinates);

            coordinates.add(geoData);
        }

        return coordinates;
    }

    protected void requireLocationHeaders() {
        DataMapping mapping = template.getDataMapping();

        if (!hasHeader(mapping.getLatitude()) || !hasHeader(mapping.getLongitude())) {
            throw new ColumnsMatchingException("Column names for latitude and longitude are not matched");
        }
    }
}