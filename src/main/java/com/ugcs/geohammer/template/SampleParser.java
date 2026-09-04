package com.ugcs.geohammer.template;

import com.google.re2j.Pattern;
import com.ugcs.geohammer.format.csv.parser.Splitter;
import com.ugcs.geohammer.template.model.Defaults;
import com.ugcs.geohammer.template.model.FormatModel;
import com.ugcs.geohammer.template.model.ParsedSample;
import com.ugcs.geohammer.template.model.TemplateModel;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Regex;
import com.ugcs.geohammer.util.Strings;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class SampleParser {

    private static final int MAX_ROWS = 25;

    private final TemplateModel templateModel;

    public SampleParser(TemplateModel templateModel) {
        this.templateModel = Check.notNull(templateModel);
    }

    public int numSkipLines(List<String> lines) {
        if (Nulls.isNullOrEmpty(lines)) {
            return 0;
        }

        int numSkipLines = 0;

        // skip to regex
        Pattern pattern = getSkipToPattern();
        if (pattern != null) {
            for (int i = 0; i < lines.size(); i++) {
                if (pattern.matches(lines.get(i))) {
                    numSkipLines = i;
                    if (templateModel.getSkipLines().isSkipMatchedLine()) {
                        numSkipLines++;
                    }
                    break;
                }
            }
        }

        // skip comments before the header / first data line
        for (int i = numSkipLines; i < lines.size(); i++) {
            if (!isBlankOrCommented(lines.get(i))) {
                break;
            }
            numSkipLines = i + 1;
        }
        return numSkipLines;
    }

    public boolean isBlankOrCommented(String line) {
        if (Strings.isNullOrBlank(line)) {
            return true;
        }
        String commentPrefix = templateModel.getFormat().getCommentPrefix();
        return !Strings.isNullOrBlank(commentPrefix) && line.trim().startsWith(commentPrefix);
    }

    public ParsedSample parseLines(List<String> lines) {
        if (Nulls.isNullOrEmpty(lines)) {
            return ParsedSample.empty();
        }

        int numSkipLines = numSkipLines(lines);
        if (numSkipLines == lines.size()) {
            return ParsedSample.empty();
        }

        lines = lines.subList(numSkipLines, lines.size());
        Splitter splitter = getBestSplitter(lines.getFirst());

        List<String> headers = parseHeaders(lines, splitter);
        List<List<String>> rows = parseRows(lines, splitter);

        return new ParsedSample(headers, rows, numSkipLines);
    }

    private List<String> parseHeaders(List<String> lines, @Nullable Splitter splitter) {
        if (lines.isEmpty()) {
            return List.of();
        }

        List<String> tokens = splitLine(lines.getFirst(), splitter);
        if (templateModel.getFormat().isHasHeader()) {
            return tokens;
        } else {
            int numColumns = tokens.size();
            List<String> headers = new ArrayList<>(numColumns);
            for (int i = 0; i < numColumns; i++) {
                headers.add(Defaults.INDEX_HEADER_PREFIX + i);
            }
            return headers;
        }
    }

    private List<List<String>> parseRows(List<String> lines, @Nullable Splitter splitter) {
        int offset = templateModel.getFormat().isHasHeader() ? 1 : 0;

        List<List<String>> rows = new ArrayList<>();
        for (int i = offset; i < lines.size() && rows.size() < MAX_ROWS; i++) {
            String line = lines.get(i);
            if (isBlankOrCommented(line)) {
                continue;
            }
            rows.add(splitLine(line, splitter));
        }
        return rows;
    }

    private List<String> splitLine(String line, @Nullable Splitter splitter) {
        if (Strings.isNullOrEmpty(line)) {
            return List.of();
        }
        if (splitter == null) {
            return List.of(line.trim());
        }
        return splitter.split(line);
    }

    private @Nullable Pattern getSkipToPattern() {
        String skipRegex = templateModel.getSkipLines().getMatchRegex();
        return Regex.compile(skipRegex);
    }

    private @Nullable Splitter getBestSplitter(String line) {
        FormatModel format = templateModel.getFormat();
        List<Splitter> splitters = Splitter.ofEscaped(
                format.getSeparators(),
                format.isRepeatableSeparator());
        return Splitter.best(splitters, line);
    }
}
