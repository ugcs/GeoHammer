package com.ugcs.geohammer.format.csv.parser;

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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.regex.Pattern;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.model.Column;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.Semantic;
import com.ugcs.geohammer.model.template.DataMapping;
import com.ugcs.geohammer.model.template.SkipLinesTo;
import com.ugcs.geohammer.model.template.Template;
import com.ugcs.geohammer.model.template.data.BaseData;
import com.ugcs.geohammer.model.template.data.Date;
import com.ugcs.geohammer.model.template.data.DateTime;
import com.ugcs.geohammer.model.template.data.SensorData;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Strings;

public abstract class Parser {

    protected final Template template;

    // contains lines that were skipped during parsing
    protected List<String> skippedLines = List.of();

    // header -> index in a file headers line
    protected Map<String, Integer> headers = Map.of();

    // date and time parsed from the filename
    protected LocalDate dateFromFilename;

    public Parser(Template template) {
        this.template = Check.notNull(template);
    }

    public Template getTemplate() {
        return template;
    }

    public List<String> getSkippedLines() {
        return skippedLines;
    }

    public List<String> getHeaders() {
        return new ArrayList<>(Nulls.toEmpty(headers).keySet());
    }

    public void setHeaders(List<String> headers) {
        this.headers = new LinkedHashMap<>(headers.size());
        Nulls.toEmpty(headers).forEach(header
                -> this.headers.put(Objects.requireNonNull(header), this.headers.size()));
    }

    public boolean hasHeader(String header) {
        return headers.containsKey(header);
    }

    private boolean hasHeader(BaseData column) {
        return column != null && headers.containsKey(column.getHeader());
    }

    public List<GeoData> parse(File file) throws IOException {
        Check.notNull(file);

        // set date from filename
        dateFromFilename = null;
        Date dateColumn = template.getDataMapping().getDate();
        if (dateColumn != null && dateColumn.getSource() == Date.Source.FileName) {
            dateFromFilename = parseDateFromFilename(file.getName());
        }

        try {
            // parse data lines
            List<GeoData> values = parseFile(file);

            // timestamps could be in wrong order in the file
            if (template.isReorderByTime()) {
                values.sort(Comparator.comparing(
                        GeoData::getDateTime,
                        Comparator.nullsFirst(Comparator.naturalOrder())));
            }

            return values;
        } catch (IOException | CancellationException e) {
            throw e;
        } catch (Exception e) {
            throw new ParseException("Failed to parse " + file.getName()
                    + " with template '" + template.getName() + "': "
                    + Strings.nullToEmpty(e.getMessage()), e);
        }
    }

    private List<GeoData> parseFile(File file) throws IOException {
        Check.notNull(file);

        DataMapping mapping = template.getDataMapping();

        ColumnSchema columns;
        List<GeoData> values = new ArrayList<>();
        try (var r = new BufferedReader(new FileReader(file))) {
            // skip top lines
            skipLines(r);

            // read headers
            String[] headerTokens = readHeaders(r);
            Check.notNull(headerTokens, "No header found");

            setHeaders(Arrays.asList(headerTokens));
            if (!hasHeader(mapping.getLatitude()) || !hasHeader(mapping.getLongitude())) {
                throw new ParseException("Column names for latitude and longitude are not matched");
            }

            columns = buildColumnSchema();

            // read value lines
            String[] valueTokens;
            while ((valueTokens = readValues(r)) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
                GeoData value = parseValues(valueTokens, columns);
                if (value != null) {
                    values.add(value);
                }
            }
        }

        // decide which columns to display
        // based on loaded data values
        setColumnDisplay(columns, values);

        return values;
    }

    private void skipLines(BufferedReader r) throws IOException {
        int markLimit = 65_536;
        skippedLines = new ArrayList<>();

        // skip by pattern
        SkipLinesTo skipLinesTo = template.getSkipLinesTo();
        if (skipLinesTo != null) {
            Pattern pattern = Pattern.compile(skipLinesTo.getMatchRegex());
            while (true) {
                r.mark(markLimit);
                String line = r.readLine();
                if (line == null) {
                    r.reset();
                    break;
                }
                if (pattern.asMatchPredicate().test(line)) {
                    if (skipLinesTo.isSkipMatchedLine()) {
                        skippedLines.add(line);
                    } else {
                        r.reset();
                    }
                    break;
                }
            }
        }

        // skip commented lines before the header / first data line
        while (true) {
            r.mark(markLimit);
            String line = r.readLine();
            if (line == null) {
                r.reset();
                break;
            }
            if (isBlankOrCommented(line)) {
                skippedLines.add(line);
            } else {
                r.reset();
                break;
            }
        }
    }

    protected abstract String[] readHeaders(BufferedReader r) throws IOException;

    protected abstract String[] readValues(BufferedReader r) throws IOException;

    public boolean isBlankOrCommented(String line) {
        if (Strings.isNullOrBlank(line)) {
            return true;
        }
        String commentPrefix = template.getFileFormat().getCommentPrefix();
        return !Strings.isNullOrBlank(commentPrefix) && line.trim().startsWith(commentPrefix);
    }

    private ColumnSchema buildColumnSchema() {
        DataMapping mapping = template.getDataMapping();

        Set<String> metaHeaders = new HashSet<>();
        for (BaseData metaValue : mapping.getMetaValues()) {
            metaHeaders.add(metaValue.getHeader());
        }

        ColumnSchema columns = new ColumnSchema();

        // add all file columns
        for (String header : headers.keySet()) {
            Column column = new Column(header);
            SensorData dataValue = mapping.getDataValueByHeader(header);
            if (dataValue != null) {
                column.setSemantic(Strings.emptyToNull(dataValue.getSemantic()));
                column.setUnit(Strings.emptyToNull(dataValue.getUnits()));
                column.setReadOnly(dataValue.isReadOnly());
            }
            // mark meta columns read-only
            if (metaHeaders.contains(header)) {
                column.setReadOnly(true);
            }
            columns.addColumn(column);
        }

        // add columns declared in template but not present in a file
        for (SensorData dataValue : Nulls.toEmpty(mapping.getDataValues())) {
            if (dataValue == null) {
                continue;
            }
            String header = dataValue.getHeader();
            // present in a file
            if (hasHeader(header)) {
                continue;
            }
            String semantic = dataValue.getSemantic();
            // is line or mark column
            if (Objects.equals(semantic, Semantic.LINE.getName()) ||
                    Objects.equals(semantic, Semantic.MARK.getName())) {
                Column column = new Column(header)
                        .withSemantic(semantic)
                        .withUnit(Strings.emptyToNull(dataValue.getUnits()));
                columns.addColumn(column);
            }
        }

        return columns;
    }

    private void setColumnDisplay(ColumnSchema columns, List<GeoData> values) {
        DataMapping mapping = template.getDataMapping();

        Set<String> excludeHeaders = new HashSet<>();
        for (BaseData metaValue : mapping.getMetaValues()) {
            excludeHeaders.add(metaValue.getHeader());
        }
        // explicitly exclude mark column
        String markHeader = mapping.getHeaderBySemantic(Semantic.MARK.getName());
        if (markHeader != null) {
            excludeHeaders.add(markHeader);
        }

        for (Column column : columns) {
            String header = column.getHeader();
            // data column declared in template or having numeric values
            boolean display = !excludeHeaders.contains(header)
                    && (mapping.getDataValueByHeader(header) != null || hasNumbers(values, header));
            column.setDisplay(display);
        }
    }

    private boolean hasNumbers(List<GeoData> values, String header) {
        for (GeoData value : values) {
            if (value.getNumber(header) != null) {
                return true;
            }
        }
        return false;
    }

    private GeoData parseValues(String[] tokens, ColumnSchema columns) {
        Double latitude = parseLatitude(tokens);
        Double longitude = parseLongitude(tokens);
        if (latitude == null || longitude == null) {
            return null;
        }
        if (latitude == 0.0 && longitude == 0.0) {
            return null;
        }

        GeoData geoData = new GeoData(columns);
        geoData.setLatitude(latitude);
        geoData.setLongitude(longitude);
        geoData.setDateTime(parseDateTime(tokens));

        for (Column column : columns) {
            String header = column.getHeader();
            if (hasHeader(header)) {
                String str = Strings.emptyToNull(getString(tokens, header));
                if (str != null) {
                    Number number = parseNumber(str);
                    geoData.setValue(header, Objects.requireNonNullElse(number, str));
                }
            }
        }

        return geoData;
    }

    // value parsers

    public String getString(String[] values, String header) {
        if (values == null || header == null) {
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
            value = Text.matchPattern(value, column.getRegex());
        }
        return value;
    }

    public Number parseNumber(String value) {
        if (Strings.isNullOrBlank(value)) {
            return null;
        }
        String decimalSeparator = template.getFileFormat().getDecimalSeparator();
        if (value.indexOf(decimalSeparator) > 0) {
            return Text.parseDouble(value);
        } else {
            return Text.parseInt(value);
        }
    }

    public Number parseNumber(String[] values, String header) {
        String value = getString(values, header);
        SensorData column = template.getDataMapping().getDataValueByHeader(header);
        if (column != null) {
            value = Text.matchPattern(value, column.getRegex());
        }
        return parseNumber(value);
    }

    public Double parseLatitude(String[] values) {
        BaseData latitudeColumn = template.getDataMapping().getLatitude();
        return Text.parseDouble(getString(values, latitudeColumn));
    }

    public Double parseLongitude(String[] values) {
        BaseData longitudeColumn = template.getDataMapping().getLongitude();
        return Text.parseDouble(getString(values, longitudeColumn));
    }

    public LocalDate parseDateFromFilename(String filename) {
        Date dateColumn = template.getDataMapping().getDate();
        String value = Text.matchPattern(filename, dateColumn.getRegex(), false);
        if (Strings.isNullOrEmpty(value)) {
            throw new IncorrectDateFormatException("Incorrect file name. Cannot match date pattern");
        }

        LocalDate date = null;
        for (String format : Nulls.toEmpty(dateColumn.getAllFormats())) {
            if (Strings.isNullOrEmpty(format)) {
                continue;
            }
            date = Text.parseDate(value, format);
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
                dateTime = Text.parseGpsDateTime(value);
            } else {
                dateTime = Text.parseDateTime(value, dateTimeColumn.getFormat());
            }
        }
        if (dateTime != null) {
            return dateTime;
        }

        // date + time columns
        // date from filename + time column
        DateTime timeColumn = mapping.getTime();
        if (hasHeader(timeColumn)) {
            LocalTime time = Text.parseTime(getString(values, timeColumn), timeColumn.getFormat());
            if (time != null) {
                Date dateColumn = mapping.getDate();
                if (hasHeader(dateColumn)) {
                    LocalDate date = Text.parseDate(getString(values, dateColumn), dateColumn.getFormat());
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
            Long timestamp = Text.parseLong(getString(values, timestampColumn));
            if (timestamp != null) {
                dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.of("UTC"));
            }
        }

        return dateTime;
    }
}
