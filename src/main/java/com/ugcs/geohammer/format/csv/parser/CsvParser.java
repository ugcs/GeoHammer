package com.ugcs.geohammer.format.csv.parser;

import com.ugcs.geohammer.model.template.FileFormat;
import com.ugcs.geohammer.model.template.Template;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.util.Text;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

public class CsvParser extends Parser {

    private String separator;

    private Pattern splitPattern;

    public CsvParser(Template template) {
        super(template);
    }

    public String getSeparator() {
        return separator;
    }

    private Pattern buildSplitPattern(String separator) {
        String regex = Pattern.quote(separator);
        if (template.getFileFormat().isRepeatableSeparator()) {
            regex += "+";
        }
        return Pattern.compile(regex);
    }

    private void initSeparator(String line) {
        if (this.separator != null) {
            return;
        }

        FileFormat format = template.getFileFormat();

        // detection heuristic:
        // best separator produces most tokens on split
        String bestVariant = null;
        Pattern bestPattern = null;
        int maxTokens = 0;

        List<String> variants = format.mergeSeparators();
        for (String variant : variants) {
            variant = Text.unescape(variant);
            Pattern pattern = buildSplitPattern(variant);
            String[] tokens = pattern.split(line);
            if (tokens.length > maxTokens) {
                bestVariant = variant;
                bestPattern = pattern;
                maxTokens = tokens.length;
            }
        }

        this.separator = bestVariant;
        this.splitPattern = bestPattern;
    }

    private String[] splitLine(String line) {
        if (Strings.isNullOrEmpty(line)) {
            return new String[0];
        }
        initSeparator(line);
        Check.notNull(splitPattern);
        String[] tokens = splitPattern.split(line);
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