package com.ugcs.geohammer.format.csv.parser;

import com.ugcs.geohammer.model.template.Template;
import com.ugcs.geohammer.util.Strings;

import java.io.BufferedReader;
import java.io.IOException;

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
            // get headers by index
            return template.getDataMapping()
                    .getIndexedHeaders()
                    .toArray(new String[0]);
        }
    }

    @Override
    protected String[] readValues(BufferedReader r) throws IOException {
        String line;
        while ((line = r.readLine()) != null) {
            if (!isBlankOrCommented(line)) {
                return splitLine(line);
            }
        }
        return null;
    }
}