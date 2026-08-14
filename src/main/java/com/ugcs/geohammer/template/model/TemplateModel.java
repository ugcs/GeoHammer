package com.ugcs.geohammer.template.model;

import com.ugcs.geohammer.util.Strings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class TemplateModel {

    private final StringProperty name = new SimpleStringProperty(Strings.empty());

    private final StringProperty matchRegex = new SimpleStringProperty(Strings.empty());

    private final BooleanProperty autoMatchRegex = new SimpleBooleanProperty(true);

    private final FormatModel format = new FormatModel();

    private final SkipLinesModel skipLines = new SkipLinesModel();

    private final ColumnsModel columns = new ColumnsModel();

    private final BooleanProperty positional = new SimpleBooleanProperty(false);

    private final BooleanProperty readOnly = new SimpleBooleanProperty(false);

    private final ObjectProperty<TimeReference> timeReference = new SimpleObjectProperty<>(TimeReference.UTC);

    private final BooleanProperty reorderByTime = new SimpleBooleanProperty(false);

    private final ObjectProperty<@Nullable FileSample> fileSample = new SimpleObjectProperty<>();

    private final ObjectProperty<ParsedSample> parsedSample = new SimpleObjectProperty<>(ParsedSample.empty());

    public String getName() {
        return name.get();
    }

    public StringProperty nameProperty() {
        return name;
    }

    public String getMatchRegex() {
        return matchRegex.get();
    }

    public StringProperty matchRegexProperty() {
        return matchRegex;
    }

    public boolean isAutoMatchRegex() {
        return autoMatchRegex.get();
    }

    public BooleanProperty autoMatchRegexProperty() {
        return autoMatchRegex;
    }

    public FormatModel getFormat() {
        return format;
    }

    public SkipLinesModel getSkipLines() {
        return skipLines;
    }

    public ColumnsModel getColumns() {
        return columns;
    }

    public boolean isPositional() {
        return positional.get();
    }

    public BooleanProperty positionalProperty() {
        return positional;
    }

    public boolean isReadOnly() {
        return readOnly.get();
    }

    public BooleanProperty readOnlyProperty() {
        return readOnly;
    }

    public TimeReference getTimeReference() {
        return timeReference.get();
    }

    public ObjectProperty<TimeReference> timeReferenceProperty() {
        return timeReference;
    }

    public boolean isReorderByTime() {
        return reorderByTime.get();
    }

    public BooleanProperty reorderByTimeProperty() {
        return reorderByTime;
    }

    public @Nullable FileSample getFileSample() {
        return fileSample.get();
    }

    public ObjectProperty<@Nullable FileSample> fileSampleProperty() {
        return fileSample;
    }

    public ParsedSample getParsedSample() {
        return parsedSample.get();
    }

    public ObjectProperty<ParsedSample> parsedSampleProperty() {
        return parsedSample;
    }

    public void reset() {
        name.set(Strings.empty());
        matchRegex.set(Strings.empty());
        autoMatchRegex.set(true);
        format.reset();
        skipLines.reset();
        columns.reset();
        positional.set(false);
        readOnly.set(false);
        timeReference.set(TimeReference.UTC);
        reorderByTime.set(false);
        fileSample.set(null);
        parsedSample.set(ParsedSample.empty());
    }
}
