package com.ugcs.gprvisualizer.app.parcers;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.reader.StreamReader;

import com.ugcs.gprvisualizer.app.parcers.exceptions.IncorrectDateFormatException;
import com.ugcs.gprvisualizer.app.yaml.Template;
import com.ugcs.gprvisualizer.app.yaml.data.BaseData;
import com.ugcs.gprvisualizer.app.yaml.data.DateTime;

public abstract class Parser implements IGeoCoordinateParser {

    protected StringBuilder skippedLines;
    protected LocalDate dateFromNameOfFile;
    // private CultureInfo format;
    protected final Template template;
    private int countOfReplacedLines;

    public int getCountOfReplacedLines() {
        return countOfReplacedLines;
    }

    public void setCountOfReplacedLines(int countOfReplacedLines) {
        this.countOfReplacedLines = countOfReplacedLines;
        if (countOfReplacedLines % 100 == 0) {
            // OnOneHundredLinesReplaced?.Invoke(countOfReplacedLines);
        }
    }

    // public event Action<int> OnOneHundredLinesReplaced;

    public abstract List<GeoCoordinates> parse(String path) throws IOException;

    public abstract Result createFileWithCorrectedCoordinates(String oldFile,
            String newFile,
            Iterable<GeoCoordinates> coordinates) throws FileNotFoundException; // ,
    // CancellationTokenSource token);

    public Parser(Template template) {
        this.template = template;
    }

    protected String skipLines(BufferedReader reader) throws IOException {
        String line;
        skippedLines = new StringBuilder();
        if (template.getSkipLinesTo() != null) {
            while ((line = reader.readLine()) != null) {
                skippedLines.append(line + "\n");
                var regex = Pattern.compile(template.getSkipLinesTo().getMatchRegex());
                if (regex.asMatchPredicate().test(line)) {
                    break;
                }
            }

            if (template.getSkipLinesTo().isSkipMatchedLine()) {
                line = reader.readLine();
                skippedLines.append(line + "\n");
                return line;
            }

            return line;
        }

        return null;
    }

    protected Double parseDouble(BaseData data, String column) {
        Double result;
        if (StringUtils.hasText(data.getRegex())) {
            var match = findByRegex(data.getRegex(), column);
            if (match.matches()) {
                try {
                    result = Double.parseDouble(match.group());

                    // if (Double.TryParse(match.Value, NumberStyles.Float, format, out result)) {

                    return result;
                } catch (NumberFormatException e) {
                    return null;
                }

            }
        }

        try {
            result = Double.parseDouble(column);
            // if (Double.TryParse(match.Value, NumberStyles.Float, format, out result)) {
            return result;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    protected Integer parseInt(BaseData data, String column) {
        int result;
        if (StringUtils.hasText(data.getRegex())) {
            var match = findByRegex(data.getRegex(), column);
            if (match.matches()) {
                try {
                    result = Integer.parseInt(match.group());
                    return result;
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }

        try {
            result = Integer.parseInt(column);
            return result;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    protected LocalDateTime parseDateTime(String[] data) {

        if (template.getDataMapping().getDateTime() != null
                && template.getDataMapping().getDateTime().getIndex() != -1) {
            if (DateTime.Type.GPST.equals(template.getDataMapping().getDateTime().getType())) {
                return gpsToUTC(data[template.getDataMapping().getDateTime().getIndex()]);
            } else {
                return parseDateAndTime(template.getDataMapping().getDateTime(),
                        data[template.getDataMapping().getDateTime().getIndex()]);
            }
        }

        if (template.getDataMapping().getTime() != null
                && template.getDataMapping().getTime().getIndex() != -1
                && template.getDataMapping().getDate() != null
                && template.getDataMapping().getDate().getIndex() != null) {
            var date = parseDate(template.getDataMapping().getDate(),
                    data[(int) template.getDataMapping().getDate().getIndex()]);
            var time = parseTime(template.getDataMapping().getTime(),
                    data[(int) template.getDataMapping().getTime().getIndex()]);
            //var totalMS = calculateTotalMS(time);
            var dateTime = LocalDateTime.of(date, time); //date.plusNanos(totalMS);
            return dateTime;
        }

        if (template.getDataMapping().getTime() != null
                && template.getDataMapping().getTime().getIndex() != -1
                && dateFromNameOfFile != null) {
            var time = parseTime(template.getDataMapping().getTime(),
                    data[(int) template.getDataMapping().getTime().getIndex()]);
            //var totalMS = calculateTotalMS(time);
            var dateTime = LocalDateTime.of(dateFromNameOfFile, time);
            return dateTime;
        }

        throw new RuntimeException("Cannot parse DateTime form file");
    }

    // TODO: Add tests for new format
    private LocalDateTime gpsToUTC(String gpsTime) {
        var data = gpsTime.split(" ");
        var weeksInDays = Integer.parseInt(data[0]);
        var secondsAndMs = (Double.parseDouble(data[1]) * 1000); // , CultureInfo.InvariantCulture);
        LocalDateTime datum = LocalDateTime.of(1980, 1, 6, 0, 0, 0);
        LocalDateTime week = datum.plusDays(weeksInDays * 7);
        LocalDateTime time = week.plusSeconds((long) (secondsAndMs * 1000));
        return time;
    }


    private LocalDateTime parseDateAndTime(DateTime data, String column) {
        LocalDateTime result;
        if (StringUtils.hasText(data.getRegex())) {
            var match = findByRegex(data.getRegex(), column);
            if (match.matches()) {
                try {
                    var format = data.getFormat().replaceAll("f", "S");
                    result = LocalDateTime.parse(match.group(), DateTimeFormatter.ofPattern(format));
                    return result;
                } catch (DateTimeParseException e) {
                    return null;
                }
            }
        }

        try {
            var format = data.getFormat().replaceAll("f", "S");
            result = LocalDateTime.parse(column, DateTimeFormatter.ofPattern(format));
            return result;
        } catch (DateTimeParseException e) {
            return null;
        }
    }    

    private LocalDate parseDate(DateTime data, String column) {
        LocalDate result;
        if (StringUtils.hasText(data.getRegex())) {
            var match = findByRegex(data.getRegex(), column);
            if (match.matches()) {
                try {
                    result = LocalDate.parse(match.group(), DateTimeFormatter.ofPattern(data.getFormat()));
                    return result;
                } catch (DateTimeParseException e) {
                    return null;
                }
            }
        }

        try {
            result = LocalDate.parse(column, DateTimeFormatter.ofPattern(data.getFormat()));
            return result;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private LocalTime parseTime(DateTime data, String column) {
        LocalTime result;
        if (StringUtils.hasText(data.getRegex())) {
            var match = findByRegex(data.getRegex(), column);
            if (match.matches()) {
                try {
                    var format = data.getFormat().replaceAll("f", "S");
                    result = LocalTime.parse(match.group(), DateTimeFormatter.ofPattern(format));
                    return result;
                } catch (DateTimeParseException e) {
                    return null;
                }
            }
        }

        try {
            var format = data.getFormat().replaceAll("f", "S");
            result = LocalTime.parse(column, DateTimeFormatter.ofPattern(format));
            return result;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /* 
    private long calculateTotalMS(LocalDateTime time) {
        return time.getSecond() * 1000 + time.getMinute() * 60000 + time.getHour() * 3600000 + time.getNano();
    } 
    */

    private Matcher findByRegex(String regex, String column) {
        var r = Pattern.compile(regex);
        var m = r.matcher(column);
        return m;
    }

    public Template getTemplate() {
        return template;
    }

}
