package com.ugcs.gprvisualizer.app.parsers.csv;

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

import com.ugcs.gprvisualizer.app.parsers.GeoCoordinates;
import com.ugcs.gprvisualizer.app.parsers.NmeaCoordinates;
import com.ugcs.gprvisualizer.app.yaml.Template;

public class NmeaCsvParser extends CsvParser {

    private NumberFormat format;

    private Map<Integer, GeoCoordinates> coordinatesMap;

    public NmeaCsvParser(Template template) {
        super(template);
    }

    @Override
    public List<GeoCoordinates> parse(String path) throws FileNotFoundException {

        File logFile = new File(path);

        if (!logFile.exists()) {
            throw new FileNotFoundException("File " + path + " does not exist");
        }

        if (getTemplate() == null) {
            throw new NullPointerException("Template is not set");
        }

        if (getTemplate().getDataMapping().getDate() != null 
            && getTemplate().getDataMapping().getDate().getSource() == com.ugcs.gprvisualizer.app.yaml.data.Date.Source.FileName) {
            parseDateFromFilename(path);
        }

        List<GeoCoordinates> coordinates = new ArrayList<GeoCoordinates>();

        try (var reader = new BufferedReader(new FileReader(path))) {
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
                NmeaCoordinates nmeaCoordinate = new NmeaCoordinates(format);
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
