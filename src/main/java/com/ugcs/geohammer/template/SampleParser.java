package com.ugcs.geohammer.template;

import com.ugcs.geohammer.template.model.Defaults;
import com.ugcs.geohammer.template.model.FormatModel;
import com.ugcs.geohammer.template.model.ParsedSample;
import com.ugcs.geohammer.template.model.TemplateModel;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Regex;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.util.Text;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

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
            Predicate<String> matcher = pattern.asMatchPredicate();
            for (int i = 0; i < lines.size(); i++) {
                if (matcher.test(lines.get(i))) {
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
        Pattern splitPattern = getSplitPattern(lines.getFirst());

        List<String> headers = parseHeaders(lines, splitPattern);
        List<List<String>> rows = parseRows(lines, splitPattern);

        return new ParsedSample(headers, rows, numSkipLines);
    }

    private List<String> parseHeaders(List<String> lines, Pattern splitPattern) {
        if (lines.isEmpty()) {
            return List.of();
        }

        List<String> tokens = splitLine(lines.getFirst(), splitPattern);
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

    private List<List<String>> parseRows(List<String> lines, Pattern splitPattern) {
        int offset = templateModel.getFormat().isHasHeader() ? 1 : 0;

        List<List<String>> rows = new ArrayList<>();
        for (int i = offset; i < lines.size() && rows.size() < MAX_ROWS; i++) {
            String line = lines.get(i);
            if (isBlankOrCommented(line)) {
                continue;
            }
            rows.add(splitLine(line, splitPattern));
        }
        return rows;
    }

    private List<String> splitLine(String line, Pattern splitPattern) {
        if (Strings.isNullOrEmpty(line)) {
            return List.of();
        }
        if (splitPattern == null) {
            return List.of(line);
        }

        String[] tokens = splitPattern.split(line);
        List<String> values = new ArrayList<>(tokens.length);
        for (String token : tokens) {
            values.add(token.trim());
        }
        return values;
    }

    private @Nullable Pattern getSkipToPattern() {
        return Regex.compile(templateModel.getSkipLines().getMatchRegex());
    }

    private @Nullable Pattern getSplitPattern(String line) {
        FormatModel format = templateModel.getFormat();

        String separator = bestSeparator(line, format.getSeparators(), format.isRepeatableSeparator());
        return !Strings.isNullOrEmpty(separator)
                ? Regex.splitPattern(Text.unescape(separator), format.isRepeatableSeparator())
                : null;
    }

    // separator producing most tokens on the line
    // repeatable = true for detection
    public @Nullable String bestSeparator(String line, List<String> separators, boolean repeatable) {
        if (Strings.isNullOrEmpty(line)) {
            return null;
        }

        String bestSeparator = null;
        int maxTokens = 0;

        for (String separator : Nulls.toEmpty(separators)) {
            Pattern pattern = Regex.splitPattern(Text.unescape(separator), repeatable);
            int numTokens = pattern.split(line).length;
            if (numTokens > maxTokens) {
                bestSeparator = separator;
                maxTokens = numTokens;
            }
        }
        return bestSeparator;
    }
}
