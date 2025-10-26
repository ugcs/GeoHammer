package com.ugcs.gprvisualizer.app.parsers.csv;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;

import com.ugcs.gprvisualizer.app.parsers.*;
import com.ugcs.gprvisualizer.app.parsers.exceptions.ParseException;
import com.ugcs.gprvisualizer.app.parsers.exceptions.IncorrectDateFormatException;
import com.ugcs.gprvisualizer.app.yaml.DataMapping;
import com.ugcs.gprvisualizer.app.yaml.data.BaseData;
import com.ugcs.gprvisualizer.app.yaml.data.Date;
import com.ugcs.gprvisualizer.app.yaml.data.SensorData;
import com.ugcs.gprvisualizer.utils.Check;
import com.ugcs.gprvisualizer.utils.Nulls;
import com.ugcs.gprvisualizer.utils.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ugcs.gprvisualizer.app.yaml.Template;
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
            String message = Strings.nullToEmpty(e.getMessage())
                    + ", used template: " + template.getName();
            throw new ParseException(message, e);
        }
    }

    private List<GeoCoordinates> parseFile(File file) throws IOException {
        Check.notNull(file);
        Check.notNull(template);

        DataMapping mapping = template.getDataMapping();

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

        // date from filename
        dateFromFilename = getDateFromFilename(file.getName());

        List<GeoCoordinates> coordinates = parseData(headers, data);

        // timestamps could be in wrong order in the file
        if (template.isReorderByTime()) {
            coordinates.sort(Comparator.comparing(
                    GeoCoordinates::getDateTime,
                    Comparator.nullsFirst(Comparator.naturalOrder())));
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

    private List<String> getDataHeaders(List<String[]> data) {
        DataMapping mapping = template.getDataMapping();

        List<String> dataHeaders = new ArrayList<>();

        // data columns declared in template
        for (SensorData column : Nulls.toEmpty(mapping.getDataValues())) {
            if (column == null) {
                continue;
            }
            String header = column.getHeader();
            // present in a file
            if (hasHeader(header)) {
                dataHeaders.add(header);
                continue;
            }
            // is line or mark column
            String semantic = column.getSemantic();
            if (Objects.equals(semantic, Semantic.LINE.getName()) ||
                    Objects.equals(semantic, Semantic.MARK.getName())) {
                dataHeaders.add(header);
                continue;
            }
            // is anomaly column
            if (semantic.endsWith(Semantic.ANOMALY_SUFFIX)) {
                String sourceSemantic = semantic.substring(
                        0, semantic.length() - Semantic.ANOMALY_SUFFIX.length());
                SensorData sourceColumn = mapping.getDataValueBySemantic(sourceSemantic);
                if (hasHeader(sourceColumn)) {
                    dataHeaders.add(header);
                }
            }
        }

        // non-empty columns with numbers
        Set<String> templateHeaders = Nulls.toEmpty(mapping.getAllValues()).stream()
                .filter(Objects::nonNull)
                .map(BaseData::getHeader)
                .filter(h -> !Strings.isNullOrBlank(h))
                .collect(Collectors.toSet());

        for (String header : headers.keySet()) {
            if (templateHeaders.contains(header)) {
                continue;
            }
            if (isNonEmptyNumericColumn(data, header)) {
                dataHeaders.add(header);
            }
        }

        return dataHeaders;
    }

    private boolean isNonEmptyNumericColumn(List<String[]> data, String header) {
        if (!hasHeader(header)) {
            return false;
        }
        for (String[] values : Nulls.toEmpty(data)) {
            Number number = parseNumber(values, header);
            if (number != null) {
                return true;
            }
        }
        return false;
    }

    private List<GeoCoordinates> parseData(Map<String, Integer> headers, List<String[]> data) {
        DataMapping mapping = template.getDataMapping();

        // list of data headers to load
        var dataHeaders = getDataHeaders(data);

        List<GeoCoordinates> coordinates = new ArrayList<>();

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

            GeoData geoData = new GeoData(latitude, longitude);
            geoData.setAltitude(parseAltitude(values));
            geoData.setDateTime(parseDateTime(values));
            geoData.setMarked(parseMark(values));

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
            geoData.setSensorValues(sensorValues);
            geoData.setSourceLine(values);
            coordinates.add(geoData);
        }

        return coordinates;
    }

    protected void requireLocationHeaders() {
        DataMapping mapping = template.getDataMapping();

        if (!hasHeader(mapping.getLatitude()) || !hasHeader(mapping.getLongitude())) {
            throw new ParseException("Column names for latitude and longitude are not matched");
        }
    }

    private LocalDate getDateFromFilename(String filename) {
        Date dateColumn = template.getDataMapping().getDate();
        if (dateColumn != null && dateColumn.getSource() == Source.FileName) {
            return parseDateFromFilename(filename);
        }
        return null;
    }
}