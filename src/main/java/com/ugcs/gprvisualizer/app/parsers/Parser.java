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
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ugcs.gprvisualizer.app.parsers.exceptions.CsvParsingException;
import com.ugcs.gprvisualizer.app.parsers.exceptions.IncorrectDateFormatException;
import com.ugcs.gprvisualizer.app.yaml.DataMapping;
import com.ugcs.gprvisualizer.app.yaml.data.Date;
import com.ugcs.gprvisualizer.utils.Strings;

import com.ugcs.gprvisualizer.app.yaml.Template;
import com.ugcs.gprvisualizer.app.yaml.data.BaseData;
import com.ugcs.gprvisualizer.app.yaml.data.DateTime;

public abstract class Parser {

    protected final Template template;

    // contains lines that were skipped during parsing
    protected StringBuilder skippedLines;

    protected LocalDate dateFromFilename;

    public Parser(Template template) {
        this.template = template;
    }

    public Template getTemplate() {
        return template;
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

    public void parseDateFromFilename(String filename) {
        DataMapping mapping = template.getDataMapping();

        Pattern pattern = Pattern.compile(mapping.getDate().getRegex());
        Matcher matcher = pattern.matcher(filename);

        if (!matcher.find()) {
            throw new IncorrectDateFormatException("Incorrect file name. Set date of logging.");
        }

        List<String> formats = mapping.getDate().getFormat() != null
                ? List.of(mapping.getDate().getFormat())
                : mapping.getDate().getFormats();
        dateFromFilename = parseDate(matcher.group(), formats);
    }

    private LocalDate parseDate(String date, List<String> formats) {
        for (String format : formats) {
            try {
                return LocalDate.parse(date, DateTimeFormatter.ofPattern(format, Locale.US));
            } catch (DateTimeParseException e) {
                // do nothing
            }
        }
        throw new IncorrectDateFormatException("Incorrect date formats");
    }

    public String matchString(BaseData column, String value) {
        if (Strings.isNullOrEmpty(value)) {
            return null;
        }
        String regex = column != null
                ? Strings.trim(column.getRegex())
                : null;
        if (!Strings.isNullOrEmpty(regex)) {
            var pattern = Pattern.compile(regex);
            var matcher = pattern.matcher(value);
            return matcher.matches() ? matcher.group() : null;
        }
        return Strings.emptyToNull(Strings.trim(value));
    }

    public Number parseNumber(BaseData column, String value) {
        if (Strings.isNullOrBlank(value)) {
            return null;
        }

        String decimalSeparator = template.getFileFormat().getDecimalSeparator();
        if (value.indexOf(decimalSeparator) > 0) {
            return parseDouble(column, value);
        } else {
            return parseInt(column, value);
        }
    }

    public Double parseDouble(BaseData column, String value) {
        value = matchString(column, value);
        if (Strings.isNullOrEmpty(value)) {
            return null;
        }

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Integer parseInt(BaseData column, String value) {
        value = matchString(column, value);
        if (Strings.isNullOrEmpty(value)) {
            return null;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Long parseLong(BaseData column, String value) {
        value = matchString(column, value);
        if (Strings.isNullOrEmpty(value)) {
            return null;
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate parseDate(DateTime column, String value) {
        value = matchString(column, value);
        if (Strings.isNullOrEmpty(value)) {
            return null;
        }

        try {
            return LocalDate.parse(value, DateTimeFormatter.ofPattern(column.getFormat()));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private LocalTime parseTime(DateTime column, String value) {
        value = matchString(column, value);
        if (Strings.isNullOrEmpty(value)) {
            return null;
        }

        String format = column.getFormat().replaceAll("f", "S");
        try {
            return LocalTime.parse(value, DateTimeFormatter.ofPattern(format));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private LocalDateTime parseDateTime(DateTime column, String value) {
        value = matchString(column, value);
        if (Strings.isNullOrEmpty(value)) {
            return null;
        }

        String format = column.getFormat().replaceAll("f", "S");
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern(format));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private LocalDateTime parseGpsTime(String value) {
        var tokens = value.split(" ");
        var weeksInDays = Integer.parseInt(tokens[0]);
        var secondsAndMs = (Double.parseDouble(tokens[1]) * 1000);
        LocalDateTime datum = LocalDateTime.of(1980, 1, 6, 0, 0, 0);
        LocalDateTime week = datum.plusDays(weeksInDays * 7L);
        return week.plusSeconds((long) (secondsAndMs * 1000));
    }

    public boolean valuesContainColumn(String[] values, BaseData column) {
        if (values == null) {
            return false;
        }
        if (column == null) {
            return false;
        }
        Integer index = column.getIndex();
        return index != null && index >= 0 && index < values.length;
    }

    public LocalDateTime parseDateTime(String[] values) {
        DataMapping mapping = template.getDataMapping();

        // dateTime column
        DateTime dateTimeColumn = mapping.getDateTime();
        if (valuesContainColumn(values, dateTimeColumn)) {
            if (dateTimeColumn.getType() == DateTime.Type.GPST) {
                return parseGpsTime(values[dateTimeColumn.getIndex()]);
            } else {
                return parseDateTime(dateTimeColumn, values[dateTimeColumn.getIndex()]);
            }
        }

        // date + time columns
        Date dateColumn = mapping.getDate();
        DateTime timeColumn = mapping.getTime();
        if (valuesContainColumn(values, dateColumn)
                && valuesContainColumn(values, timeColumn)) {
            var date = parseDate(dateColumn, values[dateColumn.getIndex()]);
            if (date == null) {
                throw new CsvParsingException(null, "Header 'date' not found in a data file");
            }
            var time = parseTime(timeColumn, values[timeColumn.getIndex()]);
            if (time == null) {
                throw new CsvParsingException(null, "Header 'time' not found in a data file");
            }
            return LocalDateTime.of(date, time);
        }

        // date from file name + time column
        if (valuesContainColumn(values, timeColumn) && dateFromFilename != null) {
            var time = parseTime(timeColumn, values[timeColumn.getIndex()]);
            if (time == null) {
                throw new CsvParsingException(null, "Header 'time' not found in a data file");
            }
            return LocalDateTime.of(dateFromFilename, time);
        }

        // timestamp column
        BaseData timestampColumn = mapping.getTimestamp();
        if (valuesContainColumn(values, timestampColumn)) {
            long timestamp = parseLong(timestampColumn, values[timestampColumn.getIndex()]);
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
        }

        throw new RuntimeException("Cannot parse DateTime from file");
    }
}
