package com.ugcs.geohammer.template.model;

import com.ugcs.geohammer.util.Strings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class SkipLinesModel {

    private final StringProperty matchRegex = new SimpleStringProperty(Strings.empty());

    private final BooleanProperty skipMatchedLine = new SimpleBooleanProperty(false);

    public String getMatchRegex() {
        return matchRegex.get();
    }

    public StringProperty matchRegexProperty() {
        return matchRegex;
    }

    public boolean isSkipMatchedLine() {
        return skipMatchedLine.get();
    }

    public BooleanProperty skipMatchedLineProperty() {
        return skipMatchedLine;
    }

    public void reset() {
        matchRegex.set(Strings.empty());
        skipMatchedLine.set(false);
    }
}
