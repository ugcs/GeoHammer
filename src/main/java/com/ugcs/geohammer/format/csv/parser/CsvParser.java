package com.ugcs.geohammer.format.csv.parser;

import com.ugcs.geohammer.model.template.FileFormat;
import com.ugcs.geohammer.model.template.Template;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Strings;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;

public class CsvParser extends Parser {

    private Splitter splitter;

    public CsvParser(Template template) {
        super(template);
    }

    public String getSeparator() {
        return splitter != null ? splitter.separator() : null;
    }

    private void initSplitter(String line) {
        if (this.splitter != null) {
            return;
        }

        FileFormat format = template.getFileFormat();
        List<Splitter> splitters = Splitter.ofEscaped(
                format.mergeSeparators(),
                format.isRepeatableSeparator()
        );
        if (!splitters.isEmpty()) {
            this.splitter = Splitter.best(splitters, line);
        }
    }

    private String[] splitLine(String line) {
        if (Strings.isNullOrEmpty(line)) {
            return new String[0];
        }
        initSplitter(line);
        Check.notNull(splitter);
        return splitter.splitToArray(line);
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