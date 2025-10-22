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

    protected Map<String, Integer> headers;

    public CsvParser(Template template) {
        super(template);
    }

    public Map<String, Integer> getHeaders() {
        return headers;
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
            parseDateFromFilename(file.getName());
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
                initColumnIndices(headers);
                //updateColumnIndex(markColumn, headers);
            }

            // read data lines
            while ((line = r.readLine()) != null) {
                if (isBlankOrCommented(line)) {
                    continue;
                }

                String[] values = splitLine(line);
                // TODO check values.length == headers.length
                data.add(values);
            }
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException();
        }

        List<GeoCoordinates> coordinates = parseData(headers, data);

        // timestamps could be in wrong order in the file
        if (mapping.getTimestamp() != null && mapping.getTimestamp().getIndex() != -1) {
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
        var dataColumns = template.getDataMapping().getDataValuesByHeader();

        List<GeoCoordinates> coordinates = new ArrayList<>();

        var traceCount = 0;
        for (String[] values : data) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            if (values == null || values.length == 0) {
                continue;
            }

            BaseData latitudeColumn = mapping.getLatitude();
            Double latitude = valuesContainColumn(values, latitudeColumn)
                    ? parseDouble(latitudeColumn, values[latitudeColumn.getIndex()])
                    : null;

            BaseData longitudeColumn = mapping.getLongitude();
            Double longitude = valuesContainColumn(values, longitudeColumn)
                    ? parseDouble(longitudeColumn, values[longitudeColumn.getIndex()])
                    : null;

            if (latitude == null || longitude == null) {
                continue;
            }

            BaseData altitudeColumn = mapping.getAltitude();
            Double altitude = valuesContainColumn(values, altitudeColumn)
                    ? parseDouble(altitudeColumn, values[altitudeColumn.getIndex()])
                    : null;

            LocalDateTime dateTime = parseDateTime(values);

            BaseData traceNumberColumn = mapping.getTraceNumber();
            Integer traceNumber = valuesContainColumn(values, traceNumberColumn)
                    ? parseInt(traceNumberColumn, values[traceNumberColumn.getIndex()])
                    : null;
            if (traceNumber == null) {
                traceNumber = traceCount;
            }

            boolean marked = false;
            BaseData markColumn = mapping.getDataValueBySemantic(Semantic.MARK.getName());
            if (valuesContainColumn(values, markColumn)) {
                marked = parseInt(markColumn, values[markColumn.getIndex()]) instanceof Integer i && i == 1;
            }

            List<SensorValue> sensorValues = new ArrayList<>();
            for (String header : dataHeaders) {
                // get column index
                Integer columnIndex = headers.get(header);
                if (columnIndex == null || columnIndex < 0 || columnIndex >= values.length) {
                    continue;
                }
                // get column definition from the template
                SensorData column = dataColumns.get(header);
                // parse and add value
                Number number = parseNumber(column, values[columnIndex]);
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

    protected void initColumnIndices(Map<String, Integer> headers) {
        List<BaseData> columns = template.getDataMapping().getAllValues();
        for (BaseData column : columns) {
            updateColumnIndex(column, headers);
        }

        // TODO extract from the method
        validateColumns();
    }

    private void updateColumnIndex(BaseData column, Map<String, Integer> headers) {
        if (column == null) {
            return;
        }
        String header = column.getHeader();
        if (!Strings.isNullOrBlank(header)) {
            column.setIndex(headers.getOrDefault(header, -1));
        }
    }

    protected void validateColumns() {
        DataMapping mapping = template.getDataMapping();

        if (mapping.getLatitude().getIndex() == -1
                || mapping.getLongitude().getIndex() == -1) {
            throw new ColumnsMatchingException("Column names for latitude and longitude are not matched");
        }
    }
}