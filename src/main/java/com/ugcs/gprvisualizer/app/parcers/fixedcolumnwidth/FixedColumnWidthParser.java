package com.ugcs.gprvisualizer.app.parcers.fixedcolumnwidth;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.ugcs.gprvisualizer.app.parcers.GeoCoordinates;
import com.ugcs.gprvisualizer.app.parcers.Parser;
import com.ugcs.gprvisualizer.app.parcers.Result;
import com.ugcs.gprvisualizer.app.parcers.exceptions.ColumnsMatchingException;
import com.ugcs.gprvisualizer.app.yaml.Template;

public class FixedColumnWidthParser extends Parser {

    private NumberFormat format;

    public FixedColumnWidthParser(Template template) {
        super(template);
    }

    public List<GeoCoordinates> parse(String logPath) throws FileNotFoundException { //, IOException, ParseException {

        if (template == null) {
            throw new NullPointerException("Template is not set");
        }

        if (!new File(logPath).exists()) {
            throw new FileNotFoundException("File " + logPath + " does not exist");
        }

        List<GeoCoordinates> coordinates = new ArrayList<>();
        format = NumberFormat.getInstance(Locale.US);
        //format.setMaximumFractionDigits(template.getFileFormat().getDecimalSeparator());

        try (var reader = new BufferedReader(new FileReader(logPath))) {
                String line = skipLines(reader);

                while ((line = reader.readLine()) != null) {
                if (line.startsWith(template.getFileFormat().getCommentPrefix()) || line.trim().isEmpty()) {
                    continue;
                }
                List<String> data = new ArrayList<>();
                for (int col : template.getFileFormat().getColumnLengths()) {
                    String column = line.substring(0, col);
                    data.add(column);
                    line = line.substring(col);
                }
                GeoCoordinates geoCoordinates = parseLineData(data);
                coordinates.add(geoCoordinates);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } 

        return coordinates;
    }

    public Result createFileWithCorrectedCoordinates(String oldFile, String newFile, Iterable<GeoCoordinates> coordinates) {//, Runnable token) {
        return new Result();
    }

    private GeoCoordinates parseLineData(List<String> data) {
        LocalDateTime date = parseDateTime(new String[] {data.get(0)});
        Double lat = parseDouble(template.getDataMapping().getLatitude(), data.get(template.getDataMapping().getLatitude().getIndex()));
        Double lon = parseDouble(template.getDataMapping().getLongitude(), data.get(template.getDataMapping().getLongitude().getIndex()));
        if (date == null || lat == null || lon == null) {
            throw new ColumnsMatchingException("Invalid data");
        }
        Double alt = template.getDataMapping().getAltitude() != null ?
            parseDouble(template.getDataMapping().getAltitude(), data.get(template.getDataMapping().getAltitude().getIndex())) : null;
        return new GeoCoordinates(date, lat, lon, alt, 0);
    }
}