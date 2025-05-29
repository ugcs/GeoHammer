package com.ugcs.gprvisualizer.app.parcers.csv;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.ugcs.gprvisualizer.app.parcers.*;
import com.ugcs.gprvisualizer.app.parcers.exceptions.CSVParsingException;
import com.ugcs.gprvisualizer.app.yaml.data.SensorData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import com.ugcs.gprvisualizer.app.parcers.exceptions.ColumnsMatchingException;
import com.ugcs.gprvisualizer.app.parcers.exceptions.IncorrectDateFormatException;
import com.ugcs.gprvisualizer.app.yaml.Template;
import com.ugcs.gprvisualizer.app.yaml.data.BaseData;
import com.ugcs.gprvisualizer.app.yaml.data.Date.Source;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public class CsvParser extends Parser {

    private static final Logger log = LoggerFactory.getLogger(CsvParser.class);

    public CsvParser(Template template) {
            super(template);
        }

        @Override
        public List<GeoCoordinates> parse(String logPath) throws FileNotFoundException {

            var coordinates = new ArrayList<GeoCoordinates>();

            if (!new File(logPath).exists()) {
                throw new FileNotFoundException(String.format("File %s does not exist", logPath));
            }

            if (template == null) {
                throw new IllegalArgumentException("Template is not set");
            }

            if (template.getDataMapping().getDate() != null && Source.FileName.equals(template.getDataMapping().getDate().getSource())) {
                parseDateFromNameOfFile(new File(logPath).getName());
            }

            try (var reader = new BufferedReader(new FileReader(logPath))) {
                String line = skipLines(reader);

                var markSensorData = new SensorData() {{
                    setHeader(GeoData.Semantic.MARK.getName());
                }};

                if (template.getFileFormat().isHasHeader()) {
                    // handle empty lines and comments before header
                    boolean eof = false;
                    while (!eof && isBlankOrCommented(line)) {
                        line = reader.readLine();
                        eof = line == null;
                    }

                    findIndexesByHeaders(line);
                    setIndexByHeaderForSensorData(line, markSensorData);
                    skippedLines.append(line + System.lineSeparator());
                }

                //format = new CultureInfo("en-US", false);
                //format.NumberFormat.NumberDecimalSeparator = template.getFileFormat().getDecimalSeparator();

                var lineNumber = skippedLines.isEmpty() ? 0 : skippedLines.toString().split(System.lineSeparator()).length;

                var traceCount = 0;

                while ((line = reader.readLine()) != null) {

                    lineNumber++;

                    if (isBlankOrCommented(line)) {
                        log.warn("Row #" + line + " was commented or empty: " + line);
                        continue;
                    }

                    var data = line.split(template.getFileFormat().getSeparator());
                    if (data.length < 2) {
                        log.warn("Row #" + lineNumber + " is not correct: " + line);
                        continue;
                    }

                    if (data.length <= template.getDataMapping().getLatitude().getIndex()) {
                        log.warn("Row #" + lineNumber + " is not correct: " + line);
                        continue;
                    }
                    var lat = parseDouble(template.getDataMapping().getLatitude(), data[template.getDataMapping().getLatitude().getIndex()]);

                    if (data.length <= template.getDataMapping().getLongitude().getIndex()) {
                        log.warn("Row #" + lineNumber + " is not correct: " + line);
                        continue;
                    }
                    var lon = parseDouble(template.getDataMapping().getLongitude(), data[template.getDataMapping().getLongitude().getIndex()]);

                    if (lat == null || lon == null) {
                        log.warn("Row #" + lineNumber + " is not correct, lat or lon was not parsed: " + line);
                        continue;
                    } 

                    var alt = template.getDataMapping().getAltitude() != null 
                              && template.getDataMapping().getAltitude().getIndex() != null
                              && template.getDataMapping().getAltitude().getIndex() != -1
                              && template.getDataMapping().getAltitude().getIndex() < data.length 
                              && StringUtils.hasText(data[template.getDataMapping().getAltitude().getIndex()]) 
                        ? parseDouble(template.getDataMapping().getAltitude(), data[template.getDataMapping().getAltitude().getIndex()]) 
                        : null;

                    Integer traceNumber = null;   
                    if (template.getDataMapping().getTraceNumber() != null 
                        && template.getDataMapping().getTraceNumber().getIndex() != -1
                        && template.getDataMapping().getTraceNumber().getIndex() < data.length) {
                        traceNumber = parseInt(template.getDataMapping().getTraceNumber(), data[template.getDataMapping().getTraceNumber().getIndex()]);
                    }
                    traceNumber = traceNumber != null ? traceNumber : traceCount;

                    List<SensorValue> sensorValues = new ArrayList<>();
                    if (template.getDataMapping().getDataValues() != null) {
                        for (SensorData sensor : template.getDataMapping().getDataValues()) {
                            String sensorData = (sensor.getIndex() != null && sensor.getIndex() != -1 && sensor.getIndex() < data.length) ? data[sensor.getIndex()] : null;
                            sensorValues.add(new SensorValue(sensor.getSemantic(), sensor.getUnits(), parseNumber(sensor, sensorData)));
                        }    
                    }

                    traceCount++;

                    var date = parseDateTime(data);

                    boolean marked = false;
                    if (markSensorData.getIndex() != null && markSensorData.getIndex() != -1 && markSensorData.getIndex() < data.length) {
                        marked = parseInt(markSensorData, data[markSensorData.getIndex()]) instanceof Integer i && i == 1;
                    }

                    coordinates.add(new GeoData(marked, lineNumber, sensorValues, new GeoCoordinates(date, lat, lon, alt, traceNumber)));
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new CSVParsingException(new File(logPath), e.getMessage() + ", used template: " + template.getName());
            }

            // timestamps could be in wrong order in the file
            if (template.getDataMapping().getTimestamp() != null && template.getDataMapping().getTimestamp().getIndex() != -1) {
                coordinates.sort((o1, o2) -> o1.getDateTime().compareTo(o2.getDateTime()));
            }

            return coordinates;
        }

        protected void parseDateFromNameOfFile(String logName) {
            Pattern r = Pattern.compile(template.getDataMapping().getDate().getRegex());
            Matcher m = r.matcher(logName);
    
            if (!m.find()) {
                throw new IncorrectDateFormatException("Incorrect file name. Set date of logging.");
            }
    
            dateFromNameOfFile = template.getDataMapping().getDate().getFormat() != null ?
                    parseDate(m.group(), template.getDataMapping().getDate().getFormat()) :
                    parseDate(m.group(), template.getDataMapping().getDate().getFormats());
        }

        private LocalDate parseDate(String date, List<String> formats) {
            for (String format : formats) {
                try {
                    return parseDate(date, format);
                } catch (IncorrectDateFormatException e) {
                    // do nothing
                }
            }
            throw new IncorrectDateFormatException("Incorrect date formats");
        }

        private LocalDate parseDate(String date, String format) {
            try {
                return LocalDate.parse(date, DateTimeFormatter.ofPattern(format, Locale.US));
            } catch (DateTimeParseException e) {
                throw new IncorrectDateFormatException("Incorrect date format");
            }
        }

        /*protected void parseDateFromNameOfFile(String logName) {
            var regex = template.getDataMapping().getDate().getRegex();
            var pattern = Pattern.compile(regex);
            var matcher = pattern.matcher(logName);

            if (!matcher.matches()) {
                throw new RuntimeException("Incorrect file name. Set date of logging.");
            }

            dateFromNameOfFile = LocalDateTime.parse(matcher.group(), DateTimeFormatter.ofPattern(template.getDataMapping().getDate().getFormat(), Locale.US));
        }*/

        @Override
        public Result createFileWithCorrectedCoordinates(String oldFile, String newFile,
            Iterable<GeoCoordinates> coordinates) throws FileNotFoundException { //, CancellationTokenSource token) {
            if (!new File(oldFile).exists()) {
                throw new FileNotFoundException("File " + oldFile + " does not exist");
            }

            if (template == null) {
                throw new RuntimeException("Template is not set");
            }

            var result = new Result();

            try (var oldFileReader = new BufferedReader(new FileReader(oldFile))) {

            String line;
            var traceCount = 0;
            var countOfReplacedLines = 0;


            var correctDictionary = true;
            Map<Integer, GeoCoordinates> dict;

            try {
                dict = StreamSupport.stream(coordinates.spliterator(), false)
                    .collect(Collectors.toMap(c -> c.getTraceNumber(), Function.identity()));
            } catch (Exception e) {
                correctDictionary = false;
                dict = new HashMap<>();
                var i = 0;
                for (var coordinate : coordinates) {
                    dict.put(i, coordinate);
                    i++;
                }
            }

            try (var correctedFile = new BufferedWriter(new FileWriter(newFile))) {

            if (template.getSkipLinesTo() != null) {
                line = skipLines(oldFileReader);
                correctedFile.write(skippedLines.toString().trim());
                correctedFile.write(System.lineSeparator());
            }

            if (template.getFileFormat().isHasHeader()) {
                line = oldFileReader.readLine();
                correctedFile.write(line.replaceAll("\\s", ""));
            }

            while ((line = oldFileReader.readLine()) != null) {
                
                //if (token.isCancellationRequested) {
                //    break;
                //}

                try {
                    if (line.startsWith(template.getFileFormat().getCommentPrefix()) || !StringUtils.hasText(line)) {
                        continue;
                    }

                    var data = line.split(template.getFileFormat().getSeparator());

                    var traceNumber = template.getDataMapping().getTraceNumber().getIndex() != null && correctDictionary
                        ? Integer.parseInt(data[(int)template.getDataMapping().getTraceNumber().getIndex()])
                        : traceCount;


                    if (dict.containsKey(traceNumber)) {
                        var format = template.getFileFormat().getDecimalSeparator(); 


                        data[template.getDataMapping().getLongitude().getIndex()] = Double.toString(dict.get(traceNumber).getLongitude());
                        data[template.getDataMapping().getLatitude().getIndex()] = Double.toString(dict.get(traceNumber).getLatitude());

                        if (template.getDataMapping().getAltitude() != null 
                            && template.getDataMapping().getAltitude().getIndex() != null
                            && template.getDataMapping().getAltitude().getIndex() != -1) {
                            data[template.getDataMapping().getAltitude().getIndex()] = Double.toString(dict.get(traceNumber).getAltitude());
                        }

                        StringJoiner joiner = new StringJoiner(template.getFileFormat().getSeparator());
                        Arrays.asList(data).forEach(joiner::add);
                        correctedFile.write(joiner.toString().replaceAll("\\s", ""));

                        result.incrementCountOfReplacedLines(); //result.getCountOfReplacedLines() + 1);
                    }
                } finally {
                    result.incrementCountOfLines();
                    countOfReplacedLines++;
                    traceCount++;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    } catch (IOException e) {
        e.printStackTrace();
    }


            return result;
        }

        private void setIndexIfHeaderNotNull(BaseData dataMapping, List<String> headers) {
            String header = dataMapping != null ? dataMapping.getHeader() : null;
            if (header != null) {
                dataMapping.setIndex(headers.indexOf(header));
            }
        }

        public void setIndexByHeaderForSensorData(String header, SensorData sd) {
            List<String> headers = Arrays.stream(header.split(template.getFileFormat().getSeparator()))
                    .map(String::trim)
                    .collect(Collectors.toList());
            setIndexIfHeaderNotNull(sd, headers);
        }

        protected void findIndexesByHeaders(String line) {
            if (line == null) {
                return;
            }

            List<String> headers = Arrays.stream(line.split(template.getFileFormat().getSeparator()))
                            .map(String::trim)
                            .collect(Collectors.toList());

            setIndexIfHeaderNotNull(template.getDataMapping().getLatitude(), headers);
            setIndexIfHeaderNotNull(template.getDataMapping().getLongitude(), headers);  
            setIndexIfHeaderNotNull(template.getDataMapping().getDate(), headers);
            setIndexIfHeaderNotNull(template.getDataMapping().getTime(), headers);
            setIndexIfHeaderNotNull(template.getDataMapping().getDateTime(), headers);
            setIndexIfHeaderNotNull(template.getDataMapping().getTimestamp(), headers);
            setIndexIfHeaderNotNull(template.getDataMapping().getTraceNumber(), headers);
            setIndexIfHeaderNotNull(template.getDataMapping().getAltitude(), headers);

            if (template.getDataMapping().getDataValues() != null) {
                for (var sensor: template.getDataMapping().getDataValues()) {
                    setIndexIfHeaderNotNull(sensor, headers);
                }
            }

            if (template.getDataMapping().getLatitude().getIndex() == -1 
                || template.getDataMapping().getLongitude().getIndex() == -1) {
                throw new ColumnsMatchingException("Column names for latitude and longitude are not matched");
            }
        }
    }