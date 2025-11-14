package com.ugcs.geohammer.format.csv.parser;

import com.ugcs.geohammer.model.template.Template;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Strings;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;

public class FixedWidthParser extends Parser {

    public FixedWidthParser(Template template) {
        super(template);
    }

    public String[] splitLine(String line) {
        List<Short> widths = Nulls.toEmpty(template.getFileFormat().getColumnLengths());
        String[] tokens = new String[widths.size()];
        int offset = 0;
        for (int i = 0; i < tokens.length; i++) {
            int width = widths.get(i);
            String token = Strings.empty();
            if (offset < line.length()) {
                token = line.substring(offset, Math.min(offset + width, line.length())).trim();
            }
            tokens[i] = token;
            offset += width;
        }
        return tokens;
    }

    @Override
    protected String[] readHeaders(BufferedReader r) {
        return template.getDataMapping()
                .getIndexedHeaders()
                .toArray(new String[0]);
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
