package com.ugcs.gprvisualizer.app.parsers.csv;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.ugcs.gprvisualizer.app.parsers.GeoCoordinates;
import com.ugcs.gprvisualizer.app.parsers.GeoData;
import com.ugcs.gprvisualizer.app.parsers.Parser;
import com.ugcs.gprvisualizer.app.parsers.Semantic;
import com.ugcs.gprvisualizer.app.parsers.SensorValue;
import com.ugcs.gprvisualizer.app.parsers.exceptions.ParseException;
import com.ugcs.gprvisualizer.app.parsers.exceptions.IncorrectDateFormatException;
import com.ugcs.gprvisualizer.app.yaml.DataMapping;
import com.ugcs.gprvisualizer.app.yaml.SkipLinesTo;
import com.ugcs.gprvisualizer.app.yaml.data.BaseData;
import com.ugcs.gprvisualizer.app.yaml.data.Date;
import com.ugcs.gprvisualizer.app.yaml.data.DateTime;
import com.ugcs.gprvisualizer.app.yaml.data.SensorData;
import com.ugcs.gprvisualizer.utils.Check;
import com.ugcs.gprvisualizer.utils.Nulls;
import com.ugcs.gprvisualizer.utils.Strings;

import com.ugcs.gprvisualizer.app.yaml.Template;
import com.ugcs.gprvisualizer.app.yaml.data.Date.Source;

public class CsvParser extends Parser {

    private final Template template;

    // contains lines that were skipped during parsing
    private List<String> skippedLines;

    // header -> index in a file headers line
    protected Map<String, Integer> headers = new HashMap<>();

    protected LocalDate dateFromFilename;

    public CsvParser(Template template) {
        Check.notNull(template);

        this.template = template;
    }

    public Template getTemplate() {
        return template;
    }

    public List<String> getSkippedLines() {
        return skippedLines;
    }

    public Map<String, Integer> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, Integer> headers) {
        this.headers = headers;
    }

    public boolean hasHeader(BaseData column) {
        return column != null && headers.containsKey(column.getHeader());
    }

    public boolean hasHeader(String header) {
        return headers.containsKey(header);
    }

    @Override
    public List<GeoCoordinates> parse(String path) throws IOException {
        Check.notEmpty(path);

        File file = new File(path);

        // set date from filename
        dateFromFilename = null;
        Date dateColumn = template.getDataMapping().getDate();
        if (dateColumn != null && dateColumn.getSource() == Source.FileName) {
            dateFromFilename = parseDateFromFilename(file.getName());
        }

        try {
            // read headers and data lines
            List<String[]> data = readLines(file);

            // parse data lines
            List<GeoCoordinates> coordinates = parseLines(data);

            // timestamps could be in wrong order in the file
            if (template.isReorderByTime()) {
                coordinates.sort(Comparator.comparing(
                        GeoCoordinates::getDateTime,
                        Comparator.nullsFirst(Comparator.naturalOrder())));
            }

            return coordinates;
        } catch (IOException | CancellationException e) {
            throw e;
        } catch (Exception e) {
            throw new ParseException("Failed to parse " + file.getName()
                    + " with template '" + template.getName() + "': "
                    + Strings.nullToEmpty(e.getMessage()), e);
        }
    }

    private List<String[]> readLines(File file) throws IOException {
        DataMapping mapping = template.getDataMapping();

        List<String[]> dataLines = new ArrayList<>();
        try (var r = new BufferedReader(new FileReader(file))) {
            String line = skipLines(r);

            // read header
            if (template.getFileFormat().isHasHeader()) {
                Check.notNull(line, "No header found");

                headers = splitHeaders(line);
                if (!hasHeader(mapping.getLatitude()) || !hasHeader(mapping.getLongitude())) {
                    throw new ParseException("Column names for latitude and longitude are not matched");
                }

                // read next line
                line = r.readLine();
            } else {
                headers = new HashMap<>();
            }

            // read data lines
            while (line != null) {
                if (!isBlankOrCommented(line)) {
                    String[] values = splitLine(line);
                    dataLines.add(values);
                }
                line = r.readLine();
            }
        }
        return dataLines;
    }

    private String skipLines(BufferedReader r) throws IOException {
        skippedLines = new ArrayList<>();

        String line;
        SkipLinesTo skipLinesTo = template.getSkipLinesTo();

        // skip by pattern
        if (skipLinesTo != null) {
            Pattern pattern = Pattern.compile(skipLinesTo.getMatchRegex());
            while ((line = r.readLine()) != null) {
                if (pattern.asMatchPredicate().test(line)) {
                    if (skipLinesTo.isSkipMatchedLine()) {
                        skippedLines.add(line);
                        line = r.readLine();
                    }
                    break;
                }
                skippedLines.add(line);
            }
        } else {
            return r.readLine();
        }

        // skip commented lines before the header / first data line
        while (line != null && isBlankOrCommented(line)) {
            skippedLines.add(line);
            line = r.readLine();
        }

        return line;
    }

    private boolean isBlankOrCommented(String line) {
        if (Strings.isNullOrBlank(line)) {
            return true;
        }
        String commentPrefix = template.getFileFormat().getCommentPrefix();
        return !Strings.isNullOrBlank(commentPrefix) && line.trim().startsWith(commentPrefix);
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

    private Map<String, Integer> splitHeaders(String line) {
        String[] tokens = splitLine(line);
        Map<String, Integer> headers = new HashMap<>(tokens.length);
        for (int i = 0; i < tokens.length; i++) {
            headers.put(tokens[i], i);
        }
        return headers;
    }

    private List<GeoCoordinates> parseLines(List<String[]> data) {
        DataMapping mapping = template.getDataMapping();

        // list of data headers to load
        List<String> dataHeaders = getDataHeaders(data);
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

    // value parsers

    public String getString(String[] values, String header) {
        if (values == null) {
            return null;
        }
        Integer columnIndex = headers.get(header);
        if (columnIndex == null || columnIndex < 0 || columnIndex >= values.length) {
            return null;
        }
        return values[columnIndex];
    }

    public String getString(String[] values, BaseData column) {
        if (column == null) {
            return null;
        }
        String value = getString(values, column.getHeader());
        if (column.getRegex() != null) {
            value = matchPattern(value, column.getRegex());
        }
        return value;
    }

    public Number parseNumber(String value) {
        if (Strings.isNullOrBlank(value)) {
            return null;
        }
        String decimalSeparator = template.getFileFormat().getDecimalSeparator();
        if (value.indexOf(decimalSeparator) > 0) {
            return parseDouble(value);
        } else {
            return parseInt(value);
        }
    }

    public Number parseNumber(String[] values, String header) {
        String value = getString(values, header);
        SensorData column = template.getDataMapping().getDataValueByHeader(header);
        if (column != null) {
            value = matchPattern(value, column.getRegex());
        }
        return parseNumber(value);
    }

    public Double parseLatitude(String[] values) {
        BaseData latitudeColumn = template.getDataMapping().getLatitude();
        return parseDouble(getString(values, latitudeColumn));
    }

    public Double parseLongitude(String[] values) {
        BaseData longitudeColumn = template.getDataMapping().getLongitude();
        return parseDouble(getString(values, longitudeColumn));
    }

    public Double parseAltitude(String[] values) {
        BaseData altitudeColumn = template.getDataMapping().getAltitude();
        return parseDouble(getString(values, altitudeColumn));
    }

    public LocalDate parseDateFromFilename(String filename) {
        Date dateColumn = template.getDataMapping().getDate();
        String value = matchPattern(filename, dateColumn.getRegex(), false);
        if (Strings.isNullOrEmpty(value)) {
            throw new IncorrectDateFormatException("Incorrect file name. Cannot match date pattern");
        }

        LocalDate date = null;
        for (String format : Nulls.toEmpty(dateColumn.getAllFormats())) {
            if (Strings.isNullOrEmpty(format)) {
                continue;
            }
            date = parseDate(value, format);
            if (date != null) {
                break;
            }
        }
        if (date == null) {
            throw new IncorrectDateFormatException("Incorrect date formats");
        }
        return date;
    }

    public LocalDateTime parseDateTime(String[] values) {
        DataMapping mapping = template.getDataMapping();

        LocalDateTime dateTime = null;

        // dateTime column
        DateTime dateTimeColumn = mapping.getDateTime();
        if (hasHeader(dateTimeColumn)) {
            String value = getString(values, dateTimeColumn);
            if (dateTimeColumn.getType() == DateTime.Type.GPST) {
                dateTime = parseGpsDateTime(value);
            } else {
                dateTime = parseDateTime(value, dateTimeColumn.getFormat());
            }
        }
        if (dateTime != null) {
            return dateTime;
        }

        // date + time columns
        // date from filename + time column
        DateTime timeColumn = mapping.getTime();
        if (hasHeader(timeColumn)) {
            LocalTime time = parseTime(getString(values, timeColumn), timeColumn.getFormat());
            if (time != null) {
                Date dateColumn = mapping.getDate();
                if (hasHeader(dateColumn)) {
                    LocalDate date = parseDate(getString(values, dateColumn), dateColumn.getFormat());
                    if (date != null) {
                        dateTime = LocalDateTime.of(date, time);
                    }
                }
                if (dateTime == null && dateFromFilename != null) {
                    dateTime = LocalDateTime.of(dateFromFilename, time);
                }
            }
        }
        if (dateTime != null) {
            return dateTime;
        }

        // timestamp columns
        BaseData timestampColumn = mapping.getTimestamp();
        if (hasHeader(timestampColumn)) {
            Long timestamp = parseLong(getString(values, timestampColumn));
            if (timestamp != null) {
                dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.of("UTC"));
            }
        }

        return dateTime;
    }
}