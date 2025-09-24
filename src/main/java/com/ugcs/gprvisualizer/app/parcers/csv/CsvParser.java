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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.ugcs.gprvisualizer.app.parcers.*;
import com.ugcs.gprvisualizer.app.parcers.exceptions.CSVParsingException;
import com.ugcs.gprvisualizer.app.yaml.data.SensorData;
import com.ugcs.gprvisualizer.gpr.PrefSettings;
import com.ugcs.gprvisualizer.utils.Check;
import com.ugcs.gprvisualizer.utils.Nulls;
import com.ugcs.gprvisualizer.utils.Strings;
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

	private final PrefSettings prefSettings;

    public CsvParser(Template template, PrefSettings prefSettings) {
        super(template);
		this.prefSettings = prefSettings;
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

        List<String[]> allDataRows = new ArrayList<>();
        List<String> headers = new ArrayList<>();

        try (var reader = new BufferedReader(new FileReader(logPath))) {
            String line = skipLines(reader);
			// tells if current line value is already in a skipped list
			boolean lineSkipped = line != null;

            var markSensorData = new SensorData();
			markSensorData.setHeader(GeoData.Semantic.MARK.getName());

            if (template.getFileFormat().isHasHeader()) {
                // Handle empty lines and comments before header
                boolean eof = template.getSkipLinesTo() != null && line == null;
                while (!eof && isBlankOrCommented(line)) {
                    line = reader.readLine();
					lineSkipped = false;
                    eof = line == null;
                }
                Check.notNull(line, "No header found");
                findIndexesByHeaders(line);
                setIndexByHeaderForSensorData(line, markSensorData);
				if (!lineSkipped) {
					skippedLines.append(line).append(System.lineSeparator());
				}

                headers = Arrays.stream(line.split(template.getFileFormat().getSeparator()))
                        .map(String::trim)
                        .toList();
            }

            // Collect all data rows
            String dataLine;
            while ((dataLine = reader.readLine()) != null) {
                if (isBlankOrCommented(dataLine)) {
					continue;
				}

                var data = dataLine.split(template.getFileFormat().getSeparator());
                if (data.length < 2) {
					continue;
				}

                allDataRows.add(data);
            }

            // Second pass: identify undeclared numeric headers
            Set<String> declaredHeader = getDeclaredHeaders();
            Set<String> undeclaredNumericHeaders = findUndeclaredNumericHeaders(allDataRows, headers, declaredHeader);

            // Filter sensors to load
            List<SensorData> sensors = filterSensors(template.getDataMapping().getDataValues());

			// save the undeclared headers to pref settings
			if (!undeclaredNumericHeaders.isEmpty()) {
				for (String header : undeclaredNumericHeaders) {
					prefSettings.saveSetting(template.getName() + "." + header, Map.of("visible", "false"));
				}
			}

            // Third pass: process all rows with known undeclared headers
            var lineNumber = skippedLines.isEmpty() ? 0 : skippedLines.toString().split(System.lineSeparator()).length;

            var traceCount = 0;
            for (String[] row : allDataRows) {
                lineNumber++;

				BaseData templateLatitude = template.getDataMapping().getLatitude();
                if (row.length <= templateLatitude.getIndex()) {
                    log.warn("Row #{} is not correct: insufficient columns", lineNumber);
                    continue;
                }
                var lat = parseDouble(templateLatitude, row[templateLatitude.getIndex()]);

				BaseData templateLongitude = template.getDataMapping().getLongitude();
                if (row.length <= templateLongitude.getIndex()) {
                    log.warn("Row #{} is not correct: insufficient columns", lineNumber);
                    continue;
                }
                var lon = parseDouble(templateLongitude, row[templateLongitude.getIndex()]);

                if (lat == null || lon == null) {
                    log.warn("Row #{} is not correct, lat or lon was not parsed", lineNumber);
                    continue;
                }

                var alt = template.getDataMapping().getAltitude() != null
                        && template.getDataMapping().getAltitude().getIndex() != null
                        && template.getDataMapping().getAltitude().getIndex() != -1
                        && template.getDataMapping().getAltitude().getIndex() < row.length
                        && StringUtils.hasText(row[template.getDataMapping().getAltitude().getIndex()])
                        ? parseDouble(template.getDataMapping().getAltitude(), row[template.getDataMapping().getAltitude().getIndex()])
                        : null;

                Integer traceNumber = null;
                if (template.getDataMapping().getTraceNumber() != null
                        && template.getDataMapping().getTraceNumber().getIndex() != -1
                        && template.getDataMapping().getTraceNumber().getIndex() < row.length) {
                    traceNumber = parseInt(template.getDataMapping().getTraceNumber(), row[template.getDataMapping().getTraceNumber().getIndex()]);
                }
                traceNumber = traceNumber != null ? traceNumber : traceCount;

                List<SensorValue> sensorValues = new ArrayList<>();
                if (template.getDataMapping().getDataValues() != null) {
                    for (SensorData sensor : sensors) {
                        String sensorData = (sensor.getIndex() != null && sensor.getIndex() != -1 && sensor.getIndex() < row.length) ? row[sensor.getIndex()] : null;
                        sensorValues.add(new SensorValue(sensor.getSemantic(), sensor.getUnits(), parseNumber(sensor, sensorData)));
                    }
                }

                // Add dynamic sensors efficiently using pre-identified headers
                addNumericValuesForUndeclaredHeaders(row, headers, sensorValues, undeclaredNumericHeaders);

                traceCount++;

                var dateTime = parseDateTime(row);

                boolean marked = false;
                if (markSensorData.getIndex() != null && markSensorData.getIndex() != -1 && markSensorData.getIndex() < row.length) {
                    marked = parseInt(markSensorData, row[markSensorData.getIndex()]) instanceof Integer i && i == 1;
                }

                coordinates.add(new GeoData(marked, lineNumber, sensorValues, new GeoCoordinates(dateTime, lat, lon, alt, traceNumber)));
            }
        } catch (Exception e) {
			throw new CSVParsingException(new File(logPath), e.getMessage() + ", used template: " + template.getName());
        }

        // timestamps could be in wrong order in the file
        if (template.getDataMapping().getTimestamp() != null && template.getDataMapping().getTimestamp().getIndex() != -1) {
            coordinates.sort(Comparator.comparing(GeoCoordinates::getDateTime));
        }

        return coordinates;
    }

    private Set<String> getDeclaredHeaders() {
        Set<String> declaredHeaders = new HashSet<>();
        if (template.getDataMapping().getDataValues() != null) {
            for (SensorData sensorData : template.getDataMapping().getDataValues()) {
                if (sensorData == null) {
					continue;
				}
                if (sensorData.getHeader() != null) {
					declaredHeaders.add(sensorData.getHeader());
				}
            }
        }
		BaseData latitude = template.getDataMapping().getLatitude();
        if (latitude != null && latitude.getHeader() != null) {
            declaredHeaders.add(latitude.getHeader());
        }
		BaseData longitude = template.getDataMapping().getLongitude();
        if (longitude != null && longitude.getHeader() != null) {
            declaredHeaders.add(longitude.getHeader());
        }
		BaseData altitude = template.getDataMapping().getAltitude();
		if (altitude != null && altitude.getHeader() != null) {
			declaredHeaders.add(altitude.getHeader());
		}
		BaseData date = template.getDataMapping().getDate();
		if (date != null && date.getHeader() != null) {
			declaredHeaders.add(date.getHeader());
		}
		BaseData time = template.getDataMapping().getTime();
		if (time != null && time.getHeader() != null) {
			declaredHeaders.add(time.getHeader());
		}
		BaseData dateTime = template.getDataMapping().getDateTime();
		if (dateTime != null && dateTime.getHeader() != null) {
			declaredHeaders.add(dateTime.getHeader());
		}
		BaseData timestamp = template.getDataMapping().getTimestamp();
		if (timestamp != null && timestamp.getHeader() != null) {
			declaredHeaders.add(timestamp.getHeader());
		}
		BaseData traceNumber = template.getDataMapping().getTraceNumber();
		if (traceNumber != null && traceNumber.getHeader() != null) {
			declaredHeaders.add(traceNumber.getHeader());
		}
		
        return declaredHeaders;
    }

    private Set<String> findUndeclaredNumericHeaders(List<String[]> allDataRows, List<String> allHeaders, Set<String> declaredHeaderSet) {
        Set<String> undeclaredNumericHeaders = new HashSet<>();
        for (int i = 0; i < allHeaders.size(); i++) {
            String headerName = allHeaders.get(i);

            if (declaredHeaderSet.contains(headerName)) {
				continue;
			}

            // Check if this column contains numeric values in any row
            boolean hasNumericValue = false;
            for (String[] row : allDataRows) {
                if (i >= row.length || row[i] == null || row[i].isEmpty()) {
					continue;
				}

				Number value = parseNumber(new SensorData(), row[i]);

				if (value != null) {
					hasNumericValue = true;
					break;
				}
			}

            if (hasNumericValue) {
                undeclaredNumericHeaders.add(headerName);
            }
        }

        return undeclaredNumericHeaders;
    }

    private void addNumericValuesForUndeclaredHeaders(String[] row, List<String> headers,
													  List<SensorValue> sensorValues,
													  Set<String> undeclaredNumericHeaders) {
        for (int i = 0; i < Math.min(headers.size(), row.length); i++) {
            String headerName = headers.get(i);

            if (!undeclaredNumericHeaders.contains(headerName) || row[i] == null || row[i].isEmpty()) {
                continue;
            }

            Number value = parseNumber(new SensorData(), row[i]);
			if (value != null) {
				sensorValues.add(new SensorValue(headerName, null, value));
			}
        }
    }

    protected List<SensorData> filterSensors(List<SensorData> sensors) {
        // semantic with column in a file
        Set<String> hasColumn = Nulls.toEmpty(sensors).stream()
                .filter(Objects::nonNull)
                .filter(sensor -> sensor.getIndex() != null && sensor.getIndex() != -1)
                .map(SensorData::getSemantic)
                .filter(semantic -> !Strings.isNullOrEmpty(semantic))
                .collect(Collectors.toSet());

        return Nulls.toEmpty(sensors).stream()
                .filter(Objects::nonNull)
                .filter(sensor -> {
                    String semantic = Strings.nullToEmpty(sensor.getSemantic());

                    // present column
                    if (hasColumn.contains(semantic)) {
                        return true;
                    }

                    // is line or mark
                    if (Objects.equals(semantic, GeoData.Semantic.LINE.getName()) ||
                            Objects.equals(semantic, GeoData.Semantic.MARK.getName())) {
                        return true;
                    }

                    // is anomaly for present column
                    if (semantic.endsWith(GeoData.ANOMALY_SEMANTIC_SUFFIX)) {
                        String sourceSemantic = semantic.substring(
                                0,
                                semantic.length() - GeoData.ANOMALY_SEMANTIC_SUFFIX.length());
                        return hasColumn.contains(sourceSemantic);
                    }

                    return false;
                })
                .toList();
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
                        .collect(Collectors.toMap(GeoCoordinates::getTraceNumber, Function.identity()));
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
                                ? Integer.parseInt(data[template.getDataMapping().getTraceNumber().getIndex()])
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
                log.error("Error writing corrected file: {}", e.getMessage(), e);
            }

        } catch (IOException e) {
            log.error("Error reading original file: {}", e.getMessage(), e);
        }
        return result;
    }

    private void setIndexIfHeaderNotNull(BaseData dataMapping, List<String> headers) {
        String header = dataMapping != null ? dataMapping.getHeader() : null;
        if (header != null) {
            dataMapping.setIndex(headers.indexOf(header));
        }
    }

    public void setIndexByHeaderForSensorData(String header, SensorData sensorData) {
        List<String> headers = Arrays.stream(header.split(template.getFileFormat().getSeparator()))
                .map(String::trim)
                .collect(Collectors.toList());
        setIndexIfHeaderNotNull(sensorData, headers);
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
            for (var sensor : template.getDataMapping().getDataValues()) {
                setIndexIfHeaderNotNull(sensor, headers);
            }
        }

        if (template.getDataMapping().getLatitude().getIndex() == -1
                || template.getDataMapping().getLongitude().getIndex() == -1) {
            throw new ColumnsMatchingException("Column names for latitude and longitude are not matched");
        }
    }
}