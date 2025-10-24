package com.ugcs.gprvisualizer.app.parsers.csv;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

import com.ugcs.gprvisualizer.app.parsers.GeoCoordinates;
import com.ugcs.gprvisualizer.app.parsers.GeoData;
import com.ugcs.gprvisualizer.app.parsers.SensorValue;
import com.ugcs.gprvisualizer.app.yaml.DataMapping;
import com.ugcs.gprvisualizer.app.yaml.Template;
import com.ugcs.gprvisualizer.app.yaml.data.Date.Source;
import com.ugcs.gprvisualizer.app.yaml.data.SensorData;
import com.ugcs.gprvisualizer.utils.Check;

public class MagDroneCsvParser extends CsvParser {

    public MagDroneCsvParser(Template template) {
        super(template);
    }

    @Override
    public List<GeoCoordinates> parse(String path) throws FileNotFoundException {
        
        if (template == null) {
            throw new NullPointerException("Template is not set");
        }

        File file = new File(path);

        if (!file.exists()) {
            throw new FileNotFoundException("File " + path + " does not exist");
        }

        if (template.getDataMapping().getDate() != null && Source.FileName.equals(template.getDataMapping().getDate().getSource())) {
            parseDateFromFilename(new File(path).getName());
        }

        this.headers = new HashMap<>();

        List<GeoCoordinates> coordinates = new ArrayList<>();
        try (var reader = new BufferedReader(new FileReader(path))) {
            String line = skipLines(reader);

            if (template.getFileFormat().isHasHeader()) {
                line = skipBlankAndComments(reader, line);
                Check.notNull(line, "No header found");

                headers = parseHeaders(line);
                requireLocationHeaders();
            }

            int traceCount = 0;
            LocalDateTime firstDateTime = null;
            long timestampOfTheFirstDatetime = 0;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith(getTemplate().getFileFormat().getCommentPrefix())) {
                    continue;
                }

                String[] data = line.split(getTemplate().getFileFormat().getSeparator());
                DataMapping mapping = getTemplate().getDataMapping();

                Double lat = parseLatitude(data);
                Double lon = parseLongitude(data);
                if (lat == null || lon == null) {
                    continue;
                }

                Double alt = parseAltitude(data);
                long timestamp = parseLong(getString(data, mapping.getTimestamp()));
                Integer traceNumber = parseTraceNumber(data);
                if (traceNumber == null) {
                    traceNumber = traceCount;
                }

                List<SensorValue> sensorValues = new ArrayList<>();
                if (template.getDataMapping().getDataValues() != null) {
                    for (SensorData column : template.getDataMapping().getDataValues()) {
                        Number number = parseNumber(getString(data, column));
                        SensorValue sensorValue = new SensorValue(
                                column.getHeader(),
                                column.getUnits(),
                                number);
                        sensorValues.add(sensorValue);
                    }    
                }

                if (mapping.getTime() == null) {
                    Instant instant = Instant.ofEpochMilli(timestamp);
                    LocalDateTime date = LocalDateTime.ofInstant(instant, ZoneId.of("UTC"));
                    coordinates.add(new GeoData(false, data, sensorValues, new GeoCoordinates(date, lat, lon, alt, traceNumber)));
                }
                
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
                boolean isRowHasTime = false;
                try {
                    if (hasHeader(mapping.getTime())) {
                        sdf.parse(getString(data, mapping.getTime()));
                        isRowHasTime = true;
                    }
                } catch (ParseException e) {
                    e.printStackTrace();
                }

                if (!isRowHasTime) {
                    if (firstDateTime != null) {
                        LocalDateTime date = firstDateTime.plus(timestamp - timestampOfTheFirstDatetime, ChronoUnit.MILLIS);
                        coordinates.add(new GeoCoordinates(date, lat, lon, alt, traceNumber));
                    } else {
                        continue;
                    }
                } else {
                    LocalDateTime date = parseDateTime(data);
                    if (firstDateTime == null) {
                        firstDateTime = date;
                        timestampOfTheFirstDatetime = timestamp;
                    }
                    coordinates.add(new GeoCoordinates(date, lat, lon, alt, traceNumber));
                }

                traceCount++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return coordinates;
    }

}    