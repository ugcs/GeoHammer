package com.ugcs.geohammer.format.csv.parser;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.template.Template;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Strings;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;

public class CsvParser extends Parser {

    public CsvParser(Template template) {
        super(template);
    }

    private String[] splitLine(String line) {
        if (Strings.isNullOrEmpty(line)) {
            return new String[0];
        }
        String[] tokens = line.split(template.getFileFormat().getSeparator());
        for (int i = 0; i < tokens.length; i++) {
            tokens[i] = tokens[i].trim();
        }
        return tokens;
    }

    @Override
    protected String[] readHeaders(BufferedReader r) throws IOException {
        if (template.getFileFormat().isHasHeader()) {
            String line = r.readLine();
            if (line == null) {
                return null;
            }
            return splitLine(line);
        } else {
            return new String[0];
        }
    }

    @Override
    protected String[] readValues(BufferedReader r) throws IOException {
        String line;
        while ((line = r.readLine()) != null) {
            if (isBlankOrCommented(line)) {
                continue;
            }
            return splitLine(line);
        }
        return null;
    }

    @Override
    protected void onParsed(ColumnSchema columns, List<GeoData> values) {
        // do nothing
    }
}