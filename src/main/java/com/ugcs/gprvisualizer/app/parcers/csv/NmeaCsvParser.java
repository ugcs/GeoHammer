package com.ugcs.gprvisualizer.app.parcers.csv;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

import com.ugcs.gprvisualizer.app.parcers.GeoCoordinates;
import com.ugcs.gprvisualizer.app.parcers.NMEACoordinates;
import com.ugcs.gprvisualizer.app.yaml.Template;
import com.ugcs.gprvisualizer.app.yaml.data.Date;
import com.ugcs.gprvisualizer.app.yaml.data.Date.Source;

public class NmeaCsvParser extends CsvParser {

    private NumberFormat format;

    private Map<Integer, GeoCoordinates> coordinatesMap;

    public NmeaCsvParser(Template template) {
        super(template);
    }

    @Override
    public List<GeoCoordinates> parse(String logPath) throws FileNotFoundException {

        File logFile = new File(logPath);

        if (!logFile.exists()) {
            throw new FileNotFoundException("File " + logPath + " does not exist");
        }

        if (getTemplate() == null) {
            throw new NullPointerException("Template is not set");
        }

        if (getTemplate().getDataMapping().getDate() != null 
            && getTemplate().getDataMapping().getDate().getSource() == com.ugcs.gprvisualizer.app.yaml.data.Date.Source.FileName) {
            parseDateFromNameOfFile(logPath);
        }

        List<GeoCoordinates> coordinates = new ArrayList<GeoCoordinates>();

        try (var reader = new BufferedReader(new FileReader(logPath))) {
            String line = skipLines(reader);

            format = NumberFormat.getNumberInstance(Locale.US);
            ((DecimalFormat) format).setDecimalSeparatorAlwaysShown(false);

            int traceCount = 0;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith(getTemplate().getFileFormat().getCommentPrefix())) {
                    continue;
                }
                String[] data = line.split(getTemplate().getFileFormat().getSeparator());
                LocalDateTime date = parseDateTime(data);
                NMEACoordinates nmeaCoordinate = new NMEACoordinates(format);
                nmeaCoordinate.parseNMEAMessage(data[getTemplate().getDataMapping().getLongitude().getIndex()]);
                nmeaCoordinate.setTraceNumber(traceCount);
                nmeaCoordinate.setDateTime(date);
                traceCount++;
                coordinates.add(nmeaCoordinate);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return coordinates;
    }

}
