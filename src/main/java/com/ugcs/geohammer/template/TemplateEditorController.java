package com.ugcs.geohammer.template;

import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import com.ugcs.geohammer.format.csv.parser.Splitter;
import com.ugcs.geohammer.model.template.FileTemplates;
import com.ugcs.geohammer.model.template.Template;
import com.ugcs.geohammer.template.model.ColumnModel;
import com.ugcs.geohammer.template.model.ColumnType;
import com.ugcs.geohammer.template.model.ColumnsModel;
import com.ugcs.geohammer.template.model.Defaults;
import com.ugcs.geohammer.template.model.FileSample;
import com.ugcs.geohammer.template.model.FormatModel;
import com.ugcs.geohammer.template.model.ParsedSample;
import com.ugcs.geohammer.template.model.SkipLinesModel;
import com.ugcs.geohammer.template.model.TemplateModel;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.FileNames;
import com.ugcs.geohammer.util.ReentranceGuard;
import com.ugcs.geohammer.util.Regex;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.util.Text;
import com.ugcs.geohammer.view.Listeners;
import javafx.collections.ListChangeListener;
import javafx.beans.Observable;
import org.controlsfx.validation.ValidationResult;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class TemplateEditorController {

    private final FileTemplates fileTemplates;

    private final TemplateModel templateModel;

    private final ReentranceGuard sampleGuard = new ReentranceGuard();

    public TemplateEditorController(FileTemplates fileTemplates, TemplateModel templateModel) {
        this.fileTemplates = fileTemplates;
        this.templateModel = templateModel;

        initListeners();
    }

    private void initListeners() {
        FormatModel format = templateModel.getFormat();
        Listeners.onChange(format.commentPrefixProperty(), v -> parseSample());
        Listeners.onChange(format.repeatableSeparatorProperty(), v -> parseSample());
        format.getSeparators().addListener((ListChangeListener<String>)change -> parseSample());
        // auto match regex stays on for header-less files: there are
        // no column names to build a regex of, so it resolves to empty
        // and validation asks for a match line
        Listeners.onChange(format.hasHeaderProperty(), hasHeader -> parseSample());

        SkipLinesModel skipLines = templateModel.getSkipLines();
        Listeners.onChange(skipLines.matchRegexProperty(), v -> parseSample());
        Listeners.onChange(skipLines.skipMatchedLineProperty(), v -> parseSample());

        Listeners.onChange(templateModel.autoMatchRegexProperty(), v -> updateMatchRegex());

        // fires on column type changes through a list extractor
        templateModel.getColumns().getColumns().addListener(
                (ListChangeListener<ColumnModel>)change -> updateMatchRegex());
    }

    public void load(FileSample fileSample) {
        Check.notNull(fileSample);

        templateModel.reset();
        templateModel.fileSampleProperty().set(fileSample);
        templateModel.nameProperty().set(FileNames.removeExtension(fileSample.file().getName()));
        detectSeparator();
        parseSample();
    }

    public void parseSample() {
        FileSample fileSample = templateModel.getFileSample();
        if (fileSample == null) {
            return;
        }
        sampleGuard.run(() -> {
            ParsedSample parsedSample = new SampleParser(templateModel).parseLines(fileSample.lines());
            setParsedSample(parsedSample);
            detectColumns();
            updateMatchRegex();
        });
    }

    private void setParsedSample(ParsedSample parsedSample) {
        Map<String, ColumnModel> previousColumns = new HashMap<>();
        for (ColumnModel column : templateModel.getColumns().getColumns()) {
            previousColumns.put(column.getHeader(), column);
        }

        List<String> headers = parsedSample.headers();
        List<ColumnModel> columns = new ArrayList<>(headers.size());
        for (String header : headers) {
            ColumnModel previousColumn = previousColumns.get(header);
            columns.add(previousColumn != null ? previousColumn : new ColumnModel(header));
        }

        // columns are set first: sample listeners resolve column
        // models by a header
        templateModel.getColumns().getColumns().setAll(columns);
        templateModel.parsedSampleProperty().set(parsedSample);
    }

    public void setColumnType(@Nullable String header, ColumnType type) {
        ColumnsModel columns = templateModel.getColumns();
        columns.setColumnType(header, type);

        ColumnModel column = columns.getColumn(header);
        if (column != null) {
            detectFormats(column);
        }
    }

    private void detectSeparator() {
        FileSample fileSample = templateModel.getFileSample();
        if (fileSample == null) {
            return;
        }

        SampleParser sampleParser = new SampleParser(templateModel);
        int numSkipLines = sampleParser.numSkipLines(fileSample.lines());

        String firstLine = numSkipLines < fileSample.numLines()
                ? fileSample.line(numSkipLines)
                : null;
        List<Splitter> splitters = Splitter.ofEscaped(Defaults.MATCH_SEPARATORS, true);
        Splitter bestSplitter = Splitter.best(splitters, firstLine);
        if (bestSplitter == null) {
            return;
        }

        // escape separator as splitter unescapes it internally
        String bestSeparator = Text.escape(bestSplitter.separator());

        FormatModel format = templateModel.getFormat();
        format.getSeparators().setAll(bestSeparator);
        format.repeatableSeparatorProperty().set(" ".equals(bestSeparator));
    }

    private void detectColumns() {
        ParsedSample parsedSample = templateModel.getParsedSample();
        if (parsedSample.isEmpty()) {
            return;
        }

        ColumnsModel columns = templateModel.getColumns();
        detectColumn(ColumnType.LATITUDE, ColumnMatchers.LATITUDE, parsedSample);
        detectColumn(ColumnType.LONGITUDE, ColumnMatchers.LONGITUDE, parsedSample);
        detectColumn(ColumnType.ALTITUDE, ColumnMatchers.ALTITUDE, parsedSample);
        detectColumn(ColumnType.LINE, ColumnMatchers.LINE, parsedSample);

        // prefer separate date and time columns, then a single
        // date-time column, then a timestamp
        if (!columns.isTimeMapped()) {
            String date = ColumnMatchers.match(parsedSample, ColumnMatchers.DATE);
            String time = ColumnMatchers.match(parsedSample, ColumnMatchers.TIME);
            if (date != null && time != null && !date.equals(time)
                    && columns.getColumnType(date) == ColumnType.NONE
                    && columns.getColumnType(time) == ColumnType.NONE) {
                setColumnType(date, ColumnType.DATE);
                setColumnType(time, ColumnType.TIME);
            } else {
                detectColumn(ColumnType.DATE_TIME, ColumnMatchers.DATE_TIME, parsedSample);
            }
            if (!columns.isTimeMapped()) {
                detectColumn(ColumnType.TIMESTAMP, ColumnMatchers.TIMESTAMP, parsedSample);
            }
        }
    }

    private void detectColumn(ColumnType type, ColumnMatcher matcher, ParsedSample parsedSample) {
        ColumnsModel columns = templateModel.getColumns();
        if (columns.getColumn(type) != null) {
            return;
        }
        String header = ColumnMatchers.match(parsedSample, matcher);
        if (header != null && columns.getColumnType(header) == ColumnType.NONE) {
            setColumnType(header, type);
        }
    }

    private void detectFormats(ColumnModel column) {
        if (!column.getFormats().isEmpty()) {
            return;
        }
        List<String> values = new ArrayList<>();
        for (String value : templateModel.getParsedSample().column(column.getHeader())) {
            if (!Strings.isNullOrBlank(value)) {
                values.add(value);
            }
        }
        List<String> formats = DateTimeFormats.matchFormats(values, column.getType());
        if (!formats.isEmpty()) {
            column.getFormats().setAll(formats);
        }
    }

    public void setSkipRegex(int line) {
        FileSample fileSample = templateModel.getFileSample();
        if (fileSample == null || line < 0 || line >= fileSample.numLines()) {
            return;
        }
        String text = fileSample.line(line);
        if (Strings.isNullOrBlank(text)) {
            return;
        }

        SkipLinesModel skipLines = templateModel.getSkipLines();
        if (line == 0) {
            skipLines.matchRegexProperty().set(Strings.empty());
        } else {
            skipLines.matchRegexProperty().set(buildSkipRegex(text));
            skipLines.skipMatchedLineProperty().set(false);
        }
        detectSeparator();
    }

    private static String buildSkipRegex(String line) {
        StringBuilder regex = new StringBuilder("\\s*");
        String[] tokens = line.trim().split("\\s+");
        for (int i = 0; i < tokens.length; i++) {
            if (i > 0) {
                regex.append("\\s+");
            }
            regex.append(Regex.quote(tokens[i]));
        }
        return regex.append("\\s*").toString();
    }

    public void setMatchRegex(int line) {
        FileSample fileSample = templateModel.getFileSample();
        if (fileSample == null || line < 0 || line >= fileSample.numLines()) {
            return;
        }
        String text = fileSample.line(line);
        if (Strings.isNullOrBlank(text)) {
            return;
        }
        templateModel.autoMatchRegexProperty().set(false);
        templateModel.matchRegexProperty().set(buildMatchRegex(text));
    }

    // quotes the line tolerating changes in a whitespace
    // and generalizing numbers
    private static String buildMatchRegex(String line) {
        String trimmed = line.trim();
        StringBuilder regex = new StringBuilder("\\s*");
        Matcher matcher = Pattern.compile("\\d+(?:\\.\\d+)?|\\s+").matcher(trimmed);
        int last = 0;
        while (matcher.find()) {
            if (matcher.start() > last) {
                regex.append(Regex.quote(trimmed.substring(last, matcher.start())));
            }
            if (Character.isWhitespace(matcher.group().charAt(0))) {
                regex.append("\\s+");
            } else {
                regex.append("[-+]?\\d+(?:\\.\\d+)?");
            }
            last = matcher.end();
        }
        if (last < trimmed.length()) {
            regex.append(Regex.quote(trimmed.substring(last)));
        }
        return regex.append("\\s*").toString();
    }

    private void updateMatchRegex() {
        if (!templateModel.isAutoMatchRegex()) {
            return;
        }
        String matchRegex = TemplateFactory.createMatchRegex(templateModel);
        templateModel.matchRegexProperty().set(matchRegex);
    }

    public ValidationResult validate() {
        String name = Strings.trim(templateModel.getName());
        if (Strings.isNullOrBlank(name)) {
            return ValidationResult.fromError(null, "Enter a template name");
        }
        if (templateModel.getParsedSample().headers().isEmpty()) {
            return ValidationResult.fromError(null, "No columns found: check the separator and the header line");
        }
        ColumnsModel columns = templateModel.getColumns();
        if (columns.getColumn(ColumnType.LATITUDE) == null
                || columns.getColumn(ColumnType.LONGITUDE) == null) {
            return ValidationResult.fromError(null, "Select latitude and longitude columns");
        }
        if (Strings.isNullOrBlank(templateModel.getMatchRegex())) {
            return ValidationResult.fromError(null, "Select a match line in the raw view");
        }
        for (ColumnModel column : columns.getColumns()) {
            switch (column.getType()) {
                case DATE_TIME, DATE, TIME -> {
                    if (column.getFormats().isEmpty()) {
                        return ValidationResult.fromError(null,
                                "Enter date and time formats for the " + column.getHeader() + " column");
                    }
                }
                default -> {
                }
            }
        }
        return new ValidationResult();
    }

    public Observable[] validationDependencies() {
        return new Observable[] {
                templateModel.nameProperty(),
                templateModel.matchRegexProperty(),
                templateModel.parsedSampleProperty(),
                templateModel.getColumns().getColumns()
        };
    }

    public Template buildTemplate() {
        Template template = TemplateFactory.create(templateModel);
        template.init();
        return template;
    }

    public Template saveTemplate(File file) throws IOException {
        Check.notNull(file);

        Path templatesPath = fileTemplates.getTemplatesPath();
        if (templatesPath != null && templatesPath.equals(file.toPath().getParent())) {
            throw new IOException("Cannot save into the application templates folder");
        }

        Template template = TemplateFactory.create(templateModel);
        String yaml = TemplateWriter.toYaml(template);

        template.init();
        if (!template.isTemplateValid()) {
            throw new IOException("Template configuration is incomplete");
        }

        TemplateWriter.write(yaml, file.toPath());
        return template;
    }
}
