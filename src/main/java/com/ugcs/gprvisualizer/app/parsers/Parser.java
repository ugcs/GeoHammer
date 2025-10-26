package com.ugcs.gprvisualizer.app.parsers;

import java.io.BufferedReader;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ugcs.gprvisualizer.app.parsers.exceptions.IncorrectDateFormatException;
import com.ugcs.gprvisualizer.app.yaml.DataMapping;
import com.ugcs.gprvisualizer.app.yaml.data.Date;
import com.ugcs.gprvisualizer.app.yaml.data.SensorData;
import com.ugcs.gprvisualizer.utils.Check;
import com.ugcs.gprvisualizer.utils.Nulls;
import com.ugcs.gprvisualizer.utils.Strings;

import com.ugcs.gprvisualizer.app.yaml.Template;
import com.ugcs.gprvisualizer.app.yaml.data.BaseData;
import com.ugcs.gprvisualizer.app.yaml.data.DateTime;

public abstract class Parser {

    protected final Template template;

    protected Map<String, Integer> headers = new HashMap<>();

    // contains lines that were skipped during parsing
    protected StringBuilder skippedLines;

    protected LocalDate dateFromFilename;

    public Parser(Template template) {
        this.template = template;
    }

    public Template getTemplate() {
        return template;
    }

    public Map<String, Integer> getHeaders() {
        return headers;
    }

    public String getSkippedLines() {
        return skippedLines.toString();
    }

    public abstract List<GeoCoordinates> parse(String path) throws IOException;

    public boolean isBlankOrCommented(String line) {
        if (Strings.isNullOrBlank(line)) {
            return true;
        }
        String commentPrefix = template.getFileFormat().getCommentPrefix();
        return !Strings.isNullOrBlank(commentPrefix) && line.startsWith(commentPrefix);
    }

    public String skipLines(BufferedReader reader) throws IOException {
        skippedLines = new StringBuilder();

        if (template.getSkipLinesTo() == null) {
            return null;
        }

        var regex = Pattern.compile(template.getSkipLinesTo().getMatchRegex());

        String line;
        while ((line = reader.readLine()) != null) {
            skippedLines.append(line).append(System.lineSeparator());
            if (regex.asMatchPredicate().test(line)) {
                break;
            }
        }

        if (template.getSkipLinesTo().isSkipMatchedLine() && line != null) {
            line = reader.readLine();
            skippedLines.append(line).append(System.lineSeparator());
        }

        return line;
    }

    public String skipBlankAndComments(BufferedReader reader, String line) throws IOException {
        // Handle empty lines and comments before header
        boolean eof = template.getSkipLinesTo() != null && line == null;
        while (!eof && isBlankOrCommented(line)) {
            line = reader.readLine();
            if (line != null) {
                skippedLines.append(line).append(System.lineSeparator());
            } else {
                eof = true;
            }
        }
        return line;
    }

    public boolean hasHeader(BaseData column) {
        return column != null && headers.containsKey(column.getHeader());
    }

    public boolean hasHeader(String header) {
        return headers.containsKey(header);
    }

    public int getColumnIndex(BaseData column) {
        return column != null
                ? getColumnIndex(column.getHeader())
                : -1;
    }

    public int getColumnIndex(String header) {
        return headers.getOrDefault(header, -1);
    }

    public boolean hasValue(String[] values, BaseData column) {
        return column != null && hasValue(values, column.getHeader());
    }

    public boolean hasValue(String[] values, String header) {
        int columnIndex = getColumnIndex(header);
        return values != null && columnIndex >= 0 && columnIndex < values.length;
    }

    public String matchPattern(String value, String regex) {
        return matchPattern(value, regex, true);
    }

    public String matchPattern(String value, String regex, boolean fullMatch) {
        if (Strings.isNullOrEmpty(value)) {
            return value;
        }
        if (!Strings.isNullOrEmpty(regex)) {
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(value);

            boolean matches = fullMatch ? matcher.matches() : matcher.find();
            return matches ? matcher.group() : null;
        }
        return value;
    }

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

    // column parsers

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

    public Integer parseTraceNumber(String[] values) {
        BaseData traceNumberColumn = template.getDataMapping().getTraceNumber();
        return parseInt(getString(values, traceNumberColumn));
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
        //throw new CsvParsingException(null, "Cannot parse date and time from file");
    }

    public boolean parseMark(String[] values) {
        BaseData markColumn = template.getDataMapping().getDataValueBySemantic(Semantic.MARK.getName());
        Integer mark = parseInt(getString(values, markColumn));
        return mark != null && mark == 1;
    }

    // primitive type parsers

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

    public Double parseDouble(String value) {
        if (Strings.isNullOrBlank(value)) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Integer parseInt(String value) {
        if (Strings.isNullOrBlank(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Long parseLong(String value) {
        if (Strings.isNullOrBlank(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate parseDate(String value, String format) {
        if (Strings.isNullOrBlank(value)) {
            return null;
        }
        Check.notNull(format);
        try {
            return LocalDate.parse(value, DateTimeFormatter.ofPattern(format, Locale.US));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private LocalTime parseTime(String value, String format) {
        if (Strings.isNullOrBlank(value)) {
            return null;
        }
        Check.notNull(format);
        format = format.replaceAll("f", "S");
        try {
            return LocalTime.parse(value, DateTimeFormatter.ofPattern(format, Locale.US));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private LocalDateTime parseDateTime(String value, String format) {
        if (Strings.isNullOrBlank(value)) {
            return null;
        }
        Check.notNull(format);
        format = format.replaceAll("f", "S");
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern(format, Locale.US));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private LocalDateTime parseGpsDateTime(String value) {
        if (Strings.isNullOrBlank(value)) {
            return null;
        }
        String[] tokens = value.split(" ");
        if (tokens.length < 2) {
            return null;
        }
        try {
            int weeks = Integer.parseInt(tokens[0]);
            double seconds = Double.parseDouble(tokens[1]);
            LocalDateTime gpsEpoch = LocalDateTime.of(1980, 1, 6, 0, 0, 0);
            return gpsEpoch
                    .plusDays(weeks * 7L)
                    .plus((long) (seconds * 1000), ChronoUnit.MILLIS);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
